package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Secretaría's movement table, over a real Postgres.
 *
 * <p>This one is almost entirely SQL — two {@code GROUP BY} counts and a join chain — so there is
 * nothing a mocked port could prove. What is being pinned here is that the months come off each
 * enrolment's own date, that a student is counted once however many enrolments they hold, and that
 * the gestión really narrows the answer instead of reporting every year the school has run.
 */
class StudentMovementSummaryIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private UUID courseA;

    @BeforeEach
    void seedSchool() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        courseA = seedCourse(teacher, "A");
    }

    /**
     * An enrolment dated by hand: the column defaults to today, and a chart about months needs
     * months the test chose.
     */
    private UUID enrolledOn(String isoDate, String names, String lastNames) {
        UUID student = seedStudent(names, lastNames);
        UUID enrolment = seedEnrollment(student, courseA);
        jdbc.update(
                "UPDATE course_enrollments SET enrollment_date = ?::date "
                        + "WHERE id_course_enrollment = ?",
                isoDate,
                enrolment);
        return student;
    }

    private void withdrewOn(UUID student, String isoTimestamp, String reason) {
        jdbc.update(
                "UPDATE students SET status = 'Withdrawn', status_reason = ?, "
                        + "status_changed_at = ?::timestamp WHERE id_student = ?",
                reason,
                isoTimestamp,
                student);
    }

    private String summaryAs(UUID actor, String role) throws Exception {
        return mvc.perform(
                        get("/api/students/movement-summary")
                                .header("Authorization", "Bearer " + tokenFor(actor, role))
                                .param("id_academic_year", String.valueOf(currentAcademicYearId())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void movementSummary_countsIntakesByTheMonthEachEnrolmentCarries() throws Exception {
        enrolledOn("2026-02-10", "Ana", "Perez");
        enrolledOn("2026-02-20", "Beto", "Quispe");
        enrolledOn("2026-03-05", "Carla", "Rojas");

        String body = summaryAs(director, "Director");

        assertThat(JsonPath.<List<Integer>>read(body, "$.byMonth[?(@.month == 2)].enrolled"))
                .containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(body, "$.byMonth[?(@.month == 3)].enrolled"))
                .containsExactly(1);
    }

    @Test
    void movementSummary_countsWithdrawalsByTheMonthTheStatusChanged() throws Exception {
        UUID ana = enrolledOn("2026-02-10", "Ana", "Perez");
        enrolledOn("2026-02-11", "Beto", "Quispe");
        withdrewOn(ana, "2026-05-14 10:00:00", "Transferencia");

        String body = summaryAs(director, "Director");

        assertThat(JsonPath.<List<Integer>>read(body, "$.byMonth[?(@.month == 5)].withdrawn"))
                .containsExactly(1);
        // February saw two intakes and no departures.
        assertThat(JsonPath.<List<Integer>>read(body, "$.byMonth[?(@.month == 2)].withdrawn"))
                .containsExactly(0);
    }

    @Test
    void movementSummary_groupsWithdrawalsByReason() throws Exception {
        UUID ana = enrolledOn("2026-02-10", "Ana", "Perez");
        UUID beto = enrolledOn("2026-02-11", "Beto", "Quispe");
        UUID carla = enrolledOn("2026-02-12", "Carla", "Rojas");
        withdrewOn(ana, "2026-05-14 10:00:00", "Transferencia");
        withdrewOn(beto, "2026-06-02 10:00:00", "Transferencia");
        withdrewOn(carla, "2026-06-03 10:00:00", "Abandono");

        String body = summaryAs(director, "Director");

        assertThat(
                        JsonPath.<List<Integer>>read(
                                body, "$.byReason[?(@.reason == 'Transferencia')].students"))
                .containsExactly(2);
        assertThat(
                        JsonPath.<List<Integer>>read(
                                body, "$.byReason[?(@.reason == 'Abandono')].students"))
                .containsExactly(1);
    }

    /**
     * A child who transferred between parallels holds an enrolment in both, because {@code
     * course_enrollments} is unique on student and course and not on student and gestión. Counted
     * by enrolment they would leave the school twice.
     */
    @Test
    void movementSummary_countsAWithdrawnStudentOnceAcrossTwoEnrolments() throws Exception {
        UUID ana = enrolledOn("2026-02-10", "Ana", "Perez");
        UUID courseB = seedCourse(teacher, "B");
        seedEnrollment(ana, courseB);
        withdrewOn(ana, "2026-05-14 10:00:00", "Transferencia");

        String body = summaryAs(director, "Director");

        assertThat(JsonPath.<List<Integer>>read(body, "$.byMonth[?(@.month == 5)].withdrawn"))
                .containsExactly(1);
        assertThat(
                        JsonPath.<List<Integer>>read(
                                body, "$.byReason[?(@.reason == 'Transferencia')].students"))
                .containsExactly(1);
    }

    /** Another gestión's movement is another gestión's. */
    @Test
    void movementSummary_narrowsToTheGestionAsked() throws Exception {
        enrolledOn("2026-02-10", "Ana", "Perez");

        String body =
                mvc.perform(
                                get("/api/students/movement-summary")
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director"))
                                        // 2025 is the other gestión schema-it.sql seeds, and this
                                        // school's whole roll was enrolled in 2026.
                                        .param(
                                                "id_academic_year",
                                                String.valueOf(academicYearId(2025))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<List<Object>>read(body, "$.byMonth[*]")).isEmpty();
        assertThat(JsonPath.<List<Object>>read(body, "$.byReason[*]")).isEmpty();
    }

    /** Secretaría reads it too: enrolling and withdrawing students is their desk. */
    @Test
    void movementSummary_isOpenToTheSecretariat() throws Exception {
        UUID secretary = seedUser("Secretary", false);

        mvc.perform(
                        get("/api/students/movement-summary")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(secretary, "Secretary"))
                                .param("id_academic_year", String.valueOf(currentAcademicYearId())))
                .andExpect(status().isOk());
    }

    /**
     * A homeroom teacher has no scope over the school's intake, or over why other students left.
     */
    @Test
    void movementSummary_isClosedToATeacher() throws Exception {
        mvc.perform(
                        get("/api/students/movement-summary")
                                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                                .param("id_academic_year", String.valueOf(currentAcademicYearId())))
                .andExpect(status().isForbidden());
    }
}
