package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReport;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportDraft;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportNote;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IPedagogicalReportDomain;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The informe pedagógico's two tables, written as upserts rather than as read-then-write.
 *
 * <p>Plain SQL and not JPA, which is the one place this report parts company with the rest of the
 * gradebook. The reason is the shape of the write, not a preference: this is a document a teacher
 * saves repeatedly while drafting it, and the row it lands in is addressed by {@code (id_course,
 * trimester)} rather than by an id the caller holds. Asking JPA whether the row exists and then
 * inserting it is a check-then-act, and under {@code READ COMMITTED} two saves of the same
 * untouched report — a double-click on the button, or a client retrying a request still in flight —
 * both read nothing and both insert. The second then fails {@code uq_pedagogical_report}, which the
 * error handler turns into a 409 on an endpoint whose whole point is that saving twice leaves the
 * same document. {@code ON CONFLICT} makes the database settle it in one statement, so the race has
 * nowhere left to happen.
 *
 * <p>Nothing is mapped as an entity for the same reason: there is no navigation to a course or an
 * enrolment anywhere here. The sheet's classroom comes from {@code ICourseService} and its roster
 * from the enrolment port, both of which the service already holds.
 */
@Repository
@Transactional(readOnly = true)
public class PedagogicalReportRepositoryAdapter implements IPedagogicalReportDomain {

    private final JdbcClient jdbc;

    public PedagogicalReportRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PedagogicalReport> byCourseAndTrimester(UUID courseId, int trimester) {
        return findReport(courseId, trimester).map(row -> withNotes(row, storedNotes(row.id())));
    }

    @Override
    @Transactional
    public PedagogicalReport save(UUID courseId, int trimester, PedagogicalReportDraft draft) {
        LocalDateTime now = LocalDateTime.now();
        /*
         * One statement, and it is the whole of the concurrency story for this table. Two saves of
         * a report nobody has written yet used to be a race; here the later one simply finds the
         * conflict and updates, which is what "saving twice leaves the same document" has to mean.
         *
         * `created_at` is deliberately absent from the DO UPDATE: on a correction the row already
         * has one, and it records when the teacher first opened this report.
         */
        ReportRow report =
                jdbc.sql(
                                """
                INSERT INTO pedagogical_reports
                    (id_course, trimester, achievements, difficulties, created_at, updated_at)
                VALUES (:course, :trimester, :achievements, :difficulties, :now, :now)
                ON CONFLICT ON CONSTRAINT uq_pedagogical_report DO UPDATE
                SET achievements = EXCLUDED.achievements,
                    difficulties = EXCLUDED.difficulties,
                    updated_at   = EXCLUDED.updated_at
                RETURNING id_pedagogical_report, id_course, trimester,
                          achievements, difficulties, updated_at
            """)
                        .param("course", courseId)
                        .param("trimester", trimester)
                        .param("achievements", draft.achievements())
                        .param("difficulties", draft.difficulties())
                        .param("now", now)
                        .query(PedagogicalReportRepositoryAdapter::toReportRow)
                        .single();

        // A null note list says this save is about the prose only, so section IV is read back
        // untouched. Treating it as an empty set instead would delete every paragraph on the
        // report — see PedagogicalReportDraft on why absent and empty cannot mean the same here.
        if (draft.notes() != null) {
            writeNotes(report.id(), notesOf(draft.notes()), now);
        }
        return withNotes(report, storedNotes(report.id()));
    }

    /**
     * Brings section IV in line with the draft: the students it names are written, the rest lose
     * their paragraph.
     *
     * <p>Upserted rather than cleared and rewritten. A delete-and-reinsert answers the same on
     * every later read and still loses something: {@code created_at} records when the teacher first
     * wrote that student's paragraph, and fixing a word in it is not writing it anew. Leaving the
     * column out of the {@code DO UPDATE} is what keeps it.
     */
    private void writeNotes(
            UUID reportId, Map<UUID, PedagogicalReportNote> wanted, LocalDateTime now) {
        deleteNotesOutside(reportId, wanted.keySet());
        for (PedagogicalReportNote note : wanted.values()) {
            jdbc.sql(
                            """
                    INSERT INTO pedagogical_report_failures
                        (id_pedagogical_report, id_course_enrollment, actions, verification_source,
                         created_at, updated_at)
                    VALUES (:report, :enrollment, :actions, :source, :now, :now)
                    ON CONFLICT ON CONSTRAINT uq_pedagogical_report_failure DO UPDATE
                    SET actions             = EXCLUDED.actions,
                        verification_source = EXCLUDED.verification_source,
                        updated_at          = EXCLUDED.updated_at
                """)
                    .param("report", reportId)
                    .param("enrollment", note.courseEnrollmentId())
                    .param("actions", note.actions())
                    .param("source", note.verificationSource())
                    .param("now", now)
                    .update();
        }
    }

    /** Removes the paragraphs the draft no longer names. An empty draft removes them all. */
    private void deleteNotesOutside(UUID reportId, Collection<UUID> keep) {
        if (keep.isEmpty()) {
            // Split rather than folded into one statement: `NOT IN ()` is not valid SQL, and an
            // empty section IV is the ordinary way a teacher clears the table.
            jdbc.sql(
                            "DELETE FROM pedagogical_report_failures WHERE id_pedagogical_report = :report")
                    .param("report", reportId)
                    .update();
            return;
        }
        jdbc.sql(
                        """
                DELETE FROM pedagogical_report_failures
                WHERE id_pedagogical_report = :report
                  AND id_course_enrollment NOT IN (:keep)
            """)
                .param("report", reportId)
                .param("keep", keep)
                .update();
    }

    private Optional<ReportRow> findReport(UUID courseId, int trimester) {
        return jdbc.sql(
                        """
                SELECT id_pedagogical_report, id_course, trimester,
                       achievements, difficulties, updated_at
                FROM pedagogical_reports
                WHERE id_course = :course AND trimester = :trimester
            """)
                .param("course", courseId)
                .param("trimester", trimester)
                .query(PedagogicalReportRepositoryAdapter::toReportRow)
                .optional();
    }

    /**
     * Section IV as it stands.
     *
     * <p>Ordered by the enrolment and not left to the query plan: the rows come back as a list, and
     * a list whose order moves between loads is a document that reads differently twice off notes
     * nobody touched. The enrolment is unique within a report, so this order is total.
     */
    private List<PedagogicalReportNote> storedNotes(UUID reportId) {
        return jdbc.sql(
                        """
                SELECT id_course_enrollment, actions, verification_source
                FROM pedagogical_report_failures
                WHERE id_pedagogical_report = :report
                ORDER BY id_course_enrollment
            """)
                .param("report", reportId)
                .query(
                        (rs, rowNum) ->
                                new PedagogicalReportNote(
                                        rs.getObject("id_course_enrollment", UUID.class),
                                        rs.getString("actions"),
                                        rs.getString("verification_source")))
                .list();
    }

    /**
     * The draft's notes by enrolment, in the order they arrived.
     *
     * <p>A draft naming the same enrolment twice keeps the last of them. The screen has one box per
     * student, whose current contents are the later entry, and writing both would leave the row
     * holding whichever statement ran second anyway.
     */
    private static Map<UUID, PedagogicalReportNote> notesOf(List<PedagogicalReportNote> notes) {
        Map<UUID, PedagogicalReportNote> byEnrollment = new LinkedHashMap<>();
        for (PedagogicalReportNote note : notes) {
            byEnrollment.put(note.courseEnrollmentId(), note);
        }
        return byEnrollment;
    }

    private static PedagogicalReport withNotes(ReportRow row, List<PedagogicalReportNote> notes) {
        return new PedagogicalReport(
                row.id(),
                row.courseId(),
                row.trimester(),
                row.achievements(),
                row.difficulties(),
                notes,
                row.updatedAt());
    }

    private static ReportRow toReportRow(ResultSet rs, int rowNum) throws SQLException {
        return new ReportRow(
                rs.getObject("id_pedagogical_report", UUID.class),
                rs.getObject("id_course", UUID.class),
                rs.getInt("trimester"),
                rs.getString("achievements"),
                rs.getString("difficulties"),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }

    /** The report row on its own, before section IV is read onto it. */
    private record ReportRow(
            UUID id,
            UUID courseId,
            Integer trimester,
            String achievements,
            String difficulties,
            LocalDateTime updatedAt) {}
}
