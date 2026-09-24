package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The raw material a feature vector is built from.
 *
 * <p>Deliberately four bounded reads rather than one method that hands back finished vectors. The
 * assembling — grouping marks by dimension, keeping their order, dividing progress by what was
 * planned — is a rule about the model, and a rule buried in a JPQL query is a rule nobody can test
 * without a database. Here the database answers questions about rows, and {@code
 * RiskFeatureAssembler} answers the question about the model.
 *
 * <p>Every method takes a collection of class groups and returns everything for all of them, so a
 * sweep of the school is four queries and not four per subject.
 */
public interface IRiskFeatureDomain {

    /**
     * The active class groups of an academic year — one prediction target each.
     *
     * @param academicYear the gestión as the school says it, {@code 2026}, not the surrogate key of
     *     the row that holds it
     */
    List<UUID> activeClassGroupIds(int academicYear);

    /**
     * One mark per criterion in these class groups for this trimester, oldest first.
     *
     * <p>Ordered by when it was entered, because the model's trend feature is literally the last
     * mark minus the first. Unordered, that feature becomes noise with a sign.
     *
     * <p>Per criterion, not per stored row. A criterion is scored either directly or through the
     * activity items hanging off it, and the gradebook already treats the average of those items as
     * that criterion's score. Reading only the direct rows would not give the model fewer marks for
     * a teacher who grades by activity — it would give it none for that dimension, so the vector
     * never completes and their students are never predicted at all.
     */
    List<CriterionScoreRow> criterionScores(Collection<UUID> classGroupIds, int trimester);

    /**
     * How many criteria the teacher planned in each class group for this trimester.
     *
     * <p>The denominator of progress. Counting the marks alone cannot tell three of three from
     * three of seven, and those two mean opposite things about the same student.
     *
     * @return class groups with no criteria planned are absent from the map, not zero
     */
    Map<UUID, Integer> plannedCriteriaCount(Collection<UUID> classGroupIds, int trimester);

    /**
     * Attendance so far per student and class group, as a percentage of the days marked.
     *
     * <p>Session rows when the teacher marks attendance in the subject, falling back to the
     * course-wide daily roll when they do not — {@code attendance.id_class_group} is nullable
     * precisely because most subjects are marked at course level.
     *
     * <p>Bounded by the trimester's configured dates, and only up to today: the point of predicting
     * is to arrive before the trimester closes, so a percentage that waits for the close is a
     * percentage that arrives with the final marks.
     */
    List<AttendanceRateRow> attendanceRates(Collection<UUID> classGroupIds, int trimester);

    /**
     * One criterion's mark, and who it belongs to.
     *
     * <p>No timestamp. When the mark was entered decides where this row lands in the sequence, and
     * the sequence is already the order these arrive in — carrying the date as well would be a
     * second copy of the same fact, free to disagree with the list it came in.
     *
     * @param dimension one of {@code Being}, {@code Knowing}, {@code Doing}, {@code Deciding} — the
     *     same four names the model uses, which is why nothing translates here
     * @param score the criterion's own score: the one entered against it, or the average of its
     *     activity items
     */
    record CriterionScoreRow(
            UUID studentId, UUID classGroupId, String dimension, BigDecimal score) {}

    /**
     * @param attendancePct 0 to 100
     */
    record AttendanceRateRow(UUID studentId, UUID classGroupId, BigDecimal attendancePct) {}
}
