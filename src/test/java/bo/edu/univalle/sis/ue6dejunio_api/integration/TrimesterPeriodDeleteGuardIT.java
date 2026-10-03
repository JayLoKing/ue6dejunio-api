package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Deleting a trimester period is refused while attendance or scores sit inside it. Attendance is
 * matched on the period's date range; scores carry a trimester number and no date, so they are
 * matched on that number within the period's gestión.
 *
 * <p>Works in a gestión of its own: the three periods of 2026 are catalog rows the base class never
 * restores, and a test that deleted one would break every later class.
 */
class TrimesterPeriodDeleteGuardIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private String bearer;
    private Integer yearId;
    private UUID periodOne;
    private UUID enrollment;
    private UUID classGroup;

    @BeforeEach
    void seed() {
        UUID director = seedUser("Director", false);
        bearer = "Bearer " + tokenFor(director, "Director");
        UUID teacher = seedUser("Teacher", false);
        yearId =
                jdbc.queryForObject(
                        "INSERT INTO academic_years (year) VALUES (2998) RETURNING id_academic_year",
                        Integer.class);
        periodOne = insertPeriod(1, "2998-02-01", "2998-05-31");
        insertPeriod(2, "2998-06-01", "2998-08-31");
        UUID course = seedCourse(teacher, "A", yearId);
        classGroup = seedClassGroup(course, teacher, "Matematicas");
        enrollment = seedEnrollment(seedStudent(), course);
    }

    private UUID insertPeriod(int trimester, String start, String end) {
        return jdbc.queryForObject(
                "INSERT INTO academic_trimesters (id_academic_year, trimester, start_date, end_date) "
                        + "VALUES (?, ?, ?, ?) RETURNING id_academic_trimester",
                UUID.class,
                yearId,
                trimester,
                Date.valueOf(start),
                Date.valueOf(end));
    }

    private void attendanceOn(String date) {
        jdbc.update(
                "INSERT INTO attendance (id_course_enrollment, date, status) VALUES (?, ?, 'Present')",
                enrollment,
                Date.valueOf(date));
    }

    private void academicScoreIn(int trimester) {
        jdbc.update(
                "INSERT INTO academic_scores (id_course_enrollment, id_class_group, trimester) "
                        + "VALUES (?, ?, ?)",
                enrollment,
                classGroup,
                trimester);
    }

    private boolean periodExists(UUID id) {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM academic_trimesters WHERE id_academic_trimester = ?",
                        Integer.class,
                        id)
                > 0;
    }

    private void deleteExpecting409() throws Exception {
        mvc.perform(delete("/api/trimester-periods/" + periodOne).header("Authorization", bearer))
                .andExpect(status().isConflict());
        assertThat(periodExists(periodOne)).isTrue();
    }

    @Test
    void delete_withAttendanceInsideTheRange_returns409AndKeepsEverything() throws Exception {
        attendanceOn("2998-03-10");

        deleteExpecting409();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void delete_withAttendanceOnTheBoundaryDays_returns409() throws Exception {
        attendanceOn("2998-05-31");

        deleteExpecting409();
    }

    @Test
    void delete_withAnEventScoreInTheTrimester_returns409() throws Exception {
        UUID criterion = seedActivityCriterion(classGroup, 1, "Knowing", "Examen", "Pruebas");
        seedEventScore(enrollment, seedEvent(criterion, "Prueba 1"), 30);

        deleteExpecting409();
    }

    @Test
    void delete_withADirectCriterionScoreInTheTrimester_returns409() throws Exception {
        UUID criterion = seedCriterion(classGroup, 1, "Being", "Puntualidad");
        seedCriterionScore(enrollment, criterion, 8);

        deleteExpecting409();
    }

    @Test
    void delete_withAnAcademicScoreInTheTrimester_returns409() throws Exception {
        academicScoreIn(1);

        deleteExpecting409();
    }

    @Test
    void delete_withRecordsOnlyOutsideThePeriod_deletesIt() throws Exception {
        attendanceOn("2998-07-15"); // inside trimester 2, not 1
        academicScoreIn(2);
        seedCriterionScore(enrollment, seedCriterion(classGroup, 2, "Being", "Puntualidad"), 8);

        mvc.perform(delete("/api/trimester-periods/" + periodOne).header("Authorization", bearer))
                .andExpect(status().isNoContent());

        assertThat(periodExists(periodOne)).isFalse();
    }

    @Test
    void delete_withNoRecords_deletesIt() throws Exception {
        mvc.perform(delete("/api/trimester-periods/" + periodOne).header("Authorization", bearer))
                .andExpect(status().isNoContent());

        assertThat(periodExists(periodOne)).isFalse();
    }

    @Test
    void delete_ignoresRecordsOfAnotherGestionInTheSameTrimesterNumber() throws Exception {
        // A score for trimester 1 of the seeded 2026 course must not pin trimester 1 of 2998.
        UUID teacher = seedUser("Teacher", false);
        UUID course2026 = seedCourse(teacher, "B");
        UUID group2026 = seedClassGroup(course2026, teacher, "Matematicas");
        UUID enrollment2026 = seedEnrollment(seedStudent(), course2026);
        seedCriterionScore(enrollment2026, seedCriterion(group2026, 1, "Being", "Puntualidad"), 8);
        jdbc.update(
                "INSERT INTO academic_scores (id_course_enrollment, id_class_group, trimester) "
                        + "VALUES (?, ?, 1)",
                enrollment2026,
                group2026);

        mvc.perform(delete("/api/trimester-periods/" + periodOne).header("Authorization", bearer))
                .andExpect(status().isNoContent());
    }
}
