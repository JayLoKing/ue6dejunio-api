package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.EnrollmentStatusCount;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The per-enrolment GROUP BY behind a class group's attendance report, through the port and the
 * real JPQL against Postgres.
 *
 * <p>The attendance table holds three kinds of row side by side — the daily ones (a null class
 * group), this class group's sessions and every other class group's sessions — and a unit test that
 * mocks this port cannot say which of them the query actually picks up.
 */
class SessionStatusCountsIT extends AbstractIntegrationTest {

    @Autowired private IAttendanceDomain attendanceDomain;

    private UUID language;
    private UUID math;
    private UUID ana;
    private UUID luis;

    @BeforeEach
    void seed() {
        UUID homeroom = seedUser("Teacher", false);
        UUID course = seedCourse(homeroom, "A");
        language = seedClassGroup(course, seedUser("Teacher", true), "Lenguaje");
        math = seedClassGroup(course, homeroom, "Matematicas");
        ana = seedEnrollment(seedStudent("Ana", "Aguilar"), course);
        luis = seedEnrollment(seedStudent("Luis", "Zambrana"), course);
    }

    private void seedMark(UUID enrollmentId, UUID classGroupId, LocalDate date, String status) {
        jdbc.update(
                "INSERT INTO attendance (id_attendance, id_course_enrollment, id_class_group, date, status) "
                        + "VALUES (?,?,?,?,?)",
                UUID.randomUUID(),
                enrollmentId,
                classGroupId,
                date,
                status);
    }

    @Test
    void countsOnlyThatClassGroupsSessions_neitherTheDailyRowsNorAnotherGroups() {
        LocalDate day = LocalDate.of(2026, 3, 2);
        seedMark(ana, language, day, "Present");
        seedMark(ana, language, LocalDate.of(2026, 3, 3), "Absent");
        // Same student, same day: the daily mark and another subject's session must not count.
        seedMark(ana, null, day, "Absent");
        seedMark(ana, math, day, "Absent");
        seedMark(luis, math, day, "Present");

        List<EnrollmentStatusCount> rows =
                attendanceDomain.sessionStatusCountsByEnrollmentIn(language, List.of(ana, luis));

        assertThat(rows)
                .extracting(
                        EnrollmentStatusCount::courseEnrollmentId,
                        EnrollmentStatusCount::date,
                        EnrollmentStatusCount::status,
                        EnrollmentStatusCount::count)
                .containsExactlyInAnyOrder(
                        tuple(ana, day, "Present", 1L),
                        tuple(ana, LocalDate.of(2026, 3, 3), "Absent", 1L));
    }

    @Test
    void onlyAsksForTheEnrolmentsNamed() {
        seedMark(ana, language, LocalDate.of(2026, 3, 2), "Present");
        seedMark(luis, language, LocalDate.of(2026, 3, 2), "Present");

        assertThat(attendanceDomain.sessionStatusCountsByEnrollmentIn(language, List.of(luis)))
                .extracting(EnrollmentStatusCount::courseEnrollmentId)
                .containsExactly(luis);
    }

    @Test
    void anEmptyPageAsksNothing() {
        assertThat(attendanceDomain.sessionStatusCountsByEnrollmentIn(language, List.of()))
                .isEmpty();
    }
}
