package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReport;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportDraft;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportNote;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IPedagogicalReportDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The informe pedagógico's own two tables, against a real Postgres.
 *
 * <p>A mocked port proves none of what is interesting here. Three things live below it and can
 * only break in the database: {@code UNIQUE (id_course, trimester)}, which is what makes a second
 * save a correction rather than a second opinion; the set semantics of the notes, where a student
 * dropped from the draft has to lose their paragraph; and whether a note that survives a save keeps
 * the row it already had, because a delete-and-reinsert would quietly reset when the teacher first
 * wrote it.
 */
class PedagogicalReportPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private IPedagogicalReportDomain reports;

    private UUID courseId;
    private UUID anaEnrollment;
    private UUID brunoEnrollment;

    @BeforeEach
    void seedClassroom() {
        UUID teacher = seedUser("Teacher", false);
        courseId = seedCourse(teacher, "A");
        anaEnrollment = seedEnrollment(seedStudent("Ana", "Alvarez"), courseId);
        brunoEnrollment = seedEnrollment(seedStudent("Bruno", "Bermudez"), courseId);
    }

    private static PedagogicalReportNote note(UUID enrollmentId, String actions, String source) {
        return new PedagogicalReportNote(enrollmentId, actions, source);
    }

    @Test
    void save_firstTime_writesEveryWrittenFieldBackIntact() {
        PedagogicalReport stored = reports.save(courseId, 1, new PedagogicalReportDraft(
            "Ana Alvarez destaca en lectura comprensiva.",
            "Dificultades en el manejo de operaciones basicas.",
            List.of(note(brunoEnrollment, "Refuerzo en horario alterno", "Cuaderno de seguimiento"))));

        assertThat(stored.id()).isNotNull();
        assertThat(stored.courseId()).isEqualTo(courseId);
        assertThat(stored.trimester()).isEqualTo(1);
        assertThat(stored.achievements()).isEqualTo("Ana Alvarez destaca en lectura comprensiva.");
        assertThat(stored.difficulties())
            .isEqualTo("Dificultades en el manejo de operaciones basicas.");
        assertThat(stored.updatedAt()).isNotNull();
        assertThat(stored.notes()).containsExactly(
            note(brunoEnrollment, "Refuerzo en horario alterno", "Cuaderno de seguimiento"));
    }

    /**
     * A half-written report has to be savable. The columns are nullable for this, and a teacher who
     * filled the failing students first and left the prose for later must not be refused.
     */
    @Test
    void save_withNothingWrittenYet_isStillADocument() {
        PedagogicalReport stored = reports.save(courseId, 2,
            new PedagogicalReportDraft(null, null, List.of()));

        assertThat(stored.id()).isNotNull();
        assertThat(stored.achievements()).isNull();
        assertThat(stored.difficulties()).isNull();
        assertThat(stored.notes()).isEmpty();
        assertThat(reports.byCourseAndTrimester(courseId, 2)).isPresent();
    }

    @Test
    void byCourseAndTrimester_neverWritten_isEmptyAndNotABlankReport() {
        assertThat(reports.byCourseAndTrimester(courseId, 3)).isEmpty();
    }

    /**
     * {@code UNIQUE (id_course, trimester)}: the school signs one informe per trimester, so the
     * second save corrects the first rather than landing beside it.
     */
    @Test
    void save_twice_correctsTheSameRowInsteadOfFilingASecond() {
        UUID firstId = reports.save(courseId, 1,
            new PedagogicalReportDraft("Primer borrador", null, List.of())).id();

        PedagogicalReport corrected = reports.save(courseId, 1,
            new PedagogicalReportDraft("Version final", "Dificultades observadas", List.of()));

        assertThat(corrected.id()).isEqualTo(firstId);
        assertThat(corrected.achievements()).isEqualTo("Version final");
        assertThat(corrected.difficulties()).isEqualTo("Dificultades observadas");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM pedagogical_reports WHERE id_course = ?", Integer.class, courseId))
            .isEqualTo(1);
    }

    /**
     * The notes are a set, not an append log. The screen holds the whole document, so a student
     * missing from the draft is a paragraph the teacher deleted.
     */
    @Test
    void save_withoutANoteThatWasThere_deletesThatStudentsParagraph() {
        reports.save(courseId, 1, new PedagogicalReportDraft(null, null, List.of(
            note(anaEnrollment, "Acciones para Ana", "Fuente Ana"),
            note(brunoEnrollment, "Acciones para Bruno", "Fuente Bruno"))));

        PedagogicalReport corrected = reports.save(courseId, 1, new PedagogicalReportDraft(
            null, null, List.of(note(brunoEnrollment, "Acciones para Bruno", "Fuente Bruno"))));

        assertThat(corrected.notes()).containsExactly(
            note(brunoEnrollment, "Acciones para Bruno", "Fuente Bruno"));
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM pedagogical_report_failures", Integer.class)).isEqualTo(1);
    }

    /**
     * A save that says nothing about section IV leaves it alone.
     *
     * <p>The difference between a null note list and an empty one is the difference between "this
     * save is about the prose" and "section IV is now empty". Read the same way, the plainest
     * possible request — the prose and nothing else — would delete every paragraph the teacher
     * wrote, and there is no second copy of those anywhere in this system.
     */
    @Test
    void save_withNullNotes_leavesEveryStoredParagraphWhereItWas() {
        reports.save(courseId, 1, new PedagogicalReportDraft(null, null, List.of(
            note(anaEnrollment, "Acciones para Ana", "Fuente Ana"),
            note(brunoEnrollment, "Acciones para Bruno", "Fuente Bruno"))));

        PedagogicalReport prose = reports.save(courseId, 1,
            new PedagogicalReportDraft("Sólo los logros", null, null));

        assertThat(prose.achievements()).isEqualTo("Sólo los logros");
        assertThat(prose.notes()).hasSize(2);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM pedagogical_report_failures", Integer.class)).isEqualTo(2);
        assertThat(reports.byCourseAndTrimester(courseId, 1).orElseThrow().notes()).hasSize(2);
    }

    /** An empty list is the other half of that rule: section IV now holds nothing. */
    @Test
    void save_withAnEmptyNoteList_clearsSectionFour() {
        reports.save(courseId, 1, new PedagogicalReportDraft(null, null,
            List.of(note(anaEnrollment, "Acciones para Ana", "Fuente Ana"))));

        PedagogicalReport cleared = reports.save(courseId, 1,
            new PedagogicalReportDraft(null, null, List.of()));

        assertThat(cleared.notes()).isEmpty();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM pedagogical_report_failures", Integer.class)).isZero();
    }

    /**
     * A surviving note is corrected in place. Clearing the table and reinserting would answer the
     * same on every read and still be wrong: the row's {@code created_at} is when the teacher first
     * wrote that paragraph, and rewriting one word is not writing it anew.
     */
    @Test
    void save_aNoteThatSurvives_keepsTheRowItAlreadyHad() {
        reports.save(courseId, 1, new PedagogicalReportDraft(null, null,
            List.of(note(brunoEnrollment, "Primera version", "Cuaderno"))));
        UUID rowId = jdbc.queryForObject(
            "SELECT id_pedagogical_report_failure FROM pedagogical_report_failures "
                + "WHERE id_course_enrollment = ?", UUID.class, brunoEnrollment);
        Timestamp createdAt = jdbc.queryForObject(
            "SELECT created_at FROM pedagogical_report_failures WHERE id_course_enrollment = ?",
            Timestamp.class, brunoEnrollment);

        reports.save(courseId, 1, new PedagogicalReportDraft(null, null,
            List.of(note(brunoEnrollment, "Version corregida", "Cuaderno"))));

        assertThat(jdbc.queryForObject(
            "SELECT id_pedagogical_report_failure FROM pedagogical_report_failures "
                + "WHERE id_course_enrollment = ?", UUID.class, brunoEnrollment))
            .isEqualTo(rowId);
        assertThat(jdbc.queryForObject(
            "SELECT created_at FROM pedagogical_report_failures WHERE id_course_enrollment = ?",
            Timestamp.class, brunoEnrollment)).isEqualTo(createdAt);
        assertThat(jdbc.queryForObject(
            "SELECT actions FROM pedagogical_report_failures WHERE id_course_enrollment = ?",
            String.class, brunoEnrollment)).isEqualTo("Version corregida");
    }

    /**
     * A save must not depend on having seen the row first.
     *
     * <p>This is the race written down as something a test can pin. A row inserted behind the
     * adapter's back stands in for the one a concurrent save committed a moment earlier: with a
     * read-then-insert, the save that never saw it would insert its own and fail
     * {@code uq_pedagogical_report} — a 409 on the endpoint whose whole promise is that saving
     * twice leaves the same document. With the upsert, it finds the conflict and corrects the row.
     */
    @Test
    void save_reportRowAlreadyThere_correctsItInsteadOfCollidingWithTheUniqueConstraint() {
        jdbc.update("INSERT INTO pedagogical_reports (id_course, trimester, achievements) "
            + "VALUES (?,?,?)", courseId, 1, "Escrito por otra transaccion");

        PedagogicalReport saved = reports.save(courseId, 1,
            new PedagogicalReportDraft("Mi version", null, List.of()));

        assertThat(saved.achievements()).isEqualTo("Mi version");
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM pedagogical_reports WHERE id_course = ?", Integer.class, courseId))
            .isEqualTo(1);
    }

    /** The same, one level down: {@code uq_pedagogical_report_failure} on a paragraph. */
    @Test
    void save_noteRowAlreadyThere_correctsItInsteadOfCollidingWithTheUniqueConstraint() {
        UUID reportId = reports.save(courseId, 1,
            new PedagogicalReportDraft(null, null, List.of())).id();
        jdbc.update("INSERT INTO pedagogical_report_failures "
                + "(id_pedagogical_report, id_course_enrollment, actions) VALUES (?,?,?)",
            reportId, brunoEnrollment, "Escrito por otra transaccion");

        PedagogicalReport saved = reports.save(courseId, 1, new PedagogicalReportDraft(null, null,
            List.of(note(brunoEnrollment, "Mi version", "Cuaderno"))));

        assertThat(saved.notes()).containsExactly(
            note(brunoEnrollment, "Mi version", "Cuaderno"));
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM pedagogical_report_failures", Integer.class)).isEqualTo(1);
    }

    /** Three trimesters, three documents. Writing the second must not reach into the first. */
    @Test
    void save_onlyTouchesTheTrimesterItNames() {
        reports.save(courseId, 1, new PedagogicalReportDraft("Logros del primero", null,
            List.of(note(anaEnrollment, "Acciones del primero", null))));

        reports.save(courseId, 2, new PedagogicalReportDraft("Logros del segundo", null, List.of()));

        Optional<PedagogicalReport> first = reports.byCourseAndTrimester(courseId, 1);
        assertThat(first).isPresent();
        assertThat(first.get().achievements()).isEqualTo("Logros del primero");
        assertThat(first.get().notes()).containsExactly(
            note(anaEnrollment, "Acciones del primero", null));
    }
}
