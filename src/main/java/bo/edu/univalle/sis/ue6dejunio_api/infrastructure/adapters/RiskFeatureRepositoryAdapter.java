package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The four reads that feed the model, as SQL.
 *
 * <p>Hand-written rather than JPQL because three of the four are aggregates the entity graph cannot
 * express — a two-level average, a count grouped by subject, and a per-day preference between two
 * sources of the same fact.
 *
 * <p>{@code status = 'Effective'} runs through all of them. A student the school took off the roll
 * keeps every mark they were ever given, and predicting them spends a slot in a bounded batch on
 * somebody who is not coming back while putting a stranger at the top of a teacher's risk list.
 */
@Repository
public class RiskFeatureRepositoryAdapter implements IRiskFeatureDomain {

    /** The enrolment status of a student actually sitting the course. */
    private static final String EFFECTIVE = "Effective";

    private final JdbcClient jdbc;

    public RiskFeatureRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> activeClassGroupIds(int academicYear) {
        return jdbc.sql(
                        """
                SELECT cg.id_class_group
                FROM class_groups cg
                JOIN courses c ON c.id_course = cg.id_course
                JOIN academic_years y ON y.id_academic_year = c.id_academic_year
                WHERE cg.is_active = true
                  AND c.is_active = true
                  AND y.year = :year
            """)
                .param("year", academicYear)
                .query(UUID.class)
                .list();
    }

    /**
     * One row per criterion, which is what the model calls a mark in a dimension.
     *
     * <p>{@code COALESCE(s.id_criterion, e.id_criterion)} and the average over it are not a
     * flourish — they are the gradebook's own rule, the same one {@code
     * JpaAssessmentScoreRepository.dimensionAverageRows} applies. A criterion is scored either
     * directly or through the activity items hanging off it, and reading only the direct scores
     * would drop every criterion a teacher chose to grade by activity. Not fewer marks for those
     * students: none, for that whole dimension, so the vector never completes and the student is
     * never predicted at all.
     *
     * <p>Ordered by the criterion's most recent mark, then by its id, then by the student. The
     * trend feature is the last mark minus the first, so the order carries meaning — and {@code
     * created_at} is nullable, has no sub-millisecond guarantee, and a teacher saving a whole sheet
     * writes several rows on one clock reading. Without a tie broken the same way every time, the
     * same data yields a different trend from one sweep to the next.
     *
     * <p>The student is in there to make the ordering total. Two students share a criterion and a
     * timestamp, so the first two keys alone leave their rows free to swap — which never reorders
     * one student's own marks, but does move where a chunked request is cut, and a run that fails
     * on a boundary that moves is a run nobody can reproduce.
     */
    @Override
    public List<CriterionScoreRow> criterionScores(Collection<UUID> classGroupIds, int trimester) {
        if (classGroupIds == null || classGroupIds.isEmpty()) {
            return List.of();
        }

        return jdbc.sql(
                        """
                SELECT ce.id_student            AS id_student,
                       c.id_class_group         AS id_class_group,
                       c.dimension              AS dimension,
                       AVG(s.score)             AS score
                FROM assessment_scores s
                LEFT JOIN assessment_events e ON e.id_assessment_event = s.id_assessment_event
                JOIN evaluation_criteria c
                     ON c.id_criterion = COALESCE(s.id_criterion, e.id_criterion)
                JOIN class_groups cg ON cg.id_class_group = c.id_class_group
                JOIN course_enrollments ce
                     ON ce.id_course_enrollment = s.id_course_enrollment
                    AND ce.id_course = cg.id_course
                WHERE c.id_class_group IN (:groups)
                  AND c.trimester = :trimester
                  AND ce.status = :effective
                GROUP BY ce.id_student, c.id_class_group, c.dimension, c.id_criterion
                ORDER BY MAX(s.created_at) ASC NULLS FIRST, c.id_criterion, ce.id_student
            """)
                .param("groups", classGroupIds)
                .param("trimester", trimester)
                .param("effective", EFFECTIVE)
                .query(
                        (rs, rowNum) ->
                                new CriterionScoreRow(
                                        rs.getObject("id_student", UUID.class),
                                        rs.getObject("id_class_group", UUID.class),
                                        rs.getString("dimension"),
                                        rs.getBigDecimal("score")))
                .list();
    }

    @Override
    public Map<UUID, Integer> plannedCriteriaCount(Collection<UUID> classGroupIds, int trimester) {
        if (classGroupIds == null || classGroupIds.isEmpty()) {
            return Map.of();
        }

        List<Map.Entry<UUID, Integer>> rows =
                jdbc.sql(
                                """
                SELECT id_class_group, COUNT(*) AS planned
                FROM evaluation_criteria
                WHERE id_class_group IN (:groups)
                  AND trimester = :trimester
                GROUP BY id_class_group
            """)
                        .param("groups", classGroupIds)
                        .param("trimester", trimester)
                        .query(
                                (rs, rowNum) ->
                                        Map.entry(
                                                rs.getObject("id_class_group", UUID.class),
                                                rs.getInt("planned")))
                        .list();

        Map<UUID, Integer> counts = new HashMap<>(rows.size());
        rows.forEach(row -> counts.put(row.getKey(), row.getValue()));
        return counts;
    }

    /**
     * Attendance so far, resolving the two places a day can be marked.
     *
     * <p>{@code attendance.id_class_group} is nullable: the course-wide daily roll leaves it null
     * and covers every subject, while a teacher marking their own period writes a row naming the
     * class group. Both can exist for the same student on the same day — the two unique indexes
     * allow exactly that — so this picks one per day rather than counting both. Counting both turns
     * one day into two, and a student marked present in the subject but absent from the roll lands
     * at 50% for a day they attended.
     *
     * <p>The subject's own row wins, because the teacher who marked their period is the one who was
     * in the room. The course-wide roll is the fallback for every day they did not.
     *
     * <p>Bounded by the trimester and by today. A percentage that waits for the trimester to close
     * arrives with the final marks, which is exactly when a prediction is worth nothing.
     *
     * <p><b>This is not the percentage the attendance panel shows, and it must not be "corrected"
     * into agreement with it.</b> {@code AttendanceCounts} answers the school's question — how much
     * class did this student actually sit — counting only {@code Present} over {@code Present +
     * Absent + Late}, with {@code Excused} excluded entirely. This one answers a different
     * question: what the model was trained on. Its training set was built from the monthly
     * spreadsheets by {@code ue6dejunio-ia}'s {@code asistencia_loader}, whose rule is {@code
     * (presentes + retrasos + licencias) / dias_habiles} — a mark of any kind counts as a day, and
     * only an outright absence counts against.
     *
     * <p>So {@code Late} and {@code Excused} both count as attended here. Feeding the model the
     * panel's figure instead would hand it a feature on a scale it never saw in training, which is
     * not a rounding difference — it is a different variable wearing the same name. The two numbers
     * are allowed to disagree because they are answers to two different questions; what is not
     * allowed is either one drifting silently.
     */
    @Override
    public List<AttendanceRateRow> attendanceRates(Collection<UUID> classGroupIds, int trimester) {
        if (classGroupIds == null || classGroupIds.isEmpty()) {
            return List.of();
        }

        return jdbc.sql(
                        """
                WITH marks AS (
                    SELECT cg.id_class_group          AS id_class_group,
                           ce.id_student              AS id_student,
                           a.status                   AS status,
                           ROW_NUMBER() OVER (
                               PARTITION BY cg.id_class_group, a.id_course_enrollment, a.date
                               ORDER BY CASE WHEN a.id_class_group IS NULL THEN 1 ELSE 0 END
                           )                          AS preference
                    FROM class_groups cg
                    JOIN courses c ON c.id_course = cg.id_course
                    JOIN academic_trimesters tr
                         ON tr.id_academic_year = c.id_academic_year
                        AND tr.trimester = :trimester
                    JOIN course_enrollments ce ON ce.id_course = cg.id_course
                    JOIN attendance a ON a.id_course_enrollment = ce.id_course_enrollment
                    WHERE cg.id_class_group IN (:groups)
                      AND ce.status = :effective
                      AND (a.id_class_group IS NULL OR a.id_class_group = cg.id_class_group)
                      AND a.date >= tr.start_date
                      AND a.date <= tr.end_date
                      AND a.date <= CURRENT_DATE
                )
                SELECT id_student,
                       id_class_group,
                       ROUND(
                           100.0 * COUNT(*) FILTER (WHERE status IN ('Present', 'Late', 'Excused'))
                           / COUNT(*), 2
                       ) AS pct
                FROM marks
                WHERE preference = 1
                GROUP BY id_student, id_class_group
            """)
                .param("groups", classGroupIds)
                .param("trimester", trimester)
                .param("effective", EFFECTIVE)
                .query(
                        (rs, rowNum) ->
                                new AttendanceRateRow(
                                        rs.getObject("id_student", UUID.class),
                                        rs.getObject("id_class_group", UUID.class),
                                        rs.getBigDecimal("pct")))
                .list();
    }
}
