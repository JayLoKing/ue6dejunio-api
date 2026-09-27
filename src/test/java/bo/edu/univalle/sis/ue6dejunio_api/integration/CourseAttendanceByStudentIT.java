package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec: RF 37 — GET /api/courses/{id}/attendance-by-student, against Postgres.
 *
 * <p>Exercises the real per-enrolment GROUP BY ({@code
 * JpaAttendanceRepository#dailyStatusCountsByEnrollmentIn}) bucketed through the
 * Director-configured periods seeded in {@code schema-it.sql} for 2026 (T1 Feb-May, T2 Jun-Aug, T3
 * Sep-Nov). A unit test cannot prove the query: it mocks the port that runs it.
 *
 * <p>The reconciliation test is the one that matters most. This report and the course panel must
 * agree, because there are already three different notions of attendance in this system and a
 * fourth would make every one of them unciteable.
 */
class CourseAttendanceByStudentIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID secretary;
    private UUID homeroomTeacher;
    private UUID courseId;
    private UUID anaEnrollment;
    private UUID luisEnrollment;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        secretary = seedUser("Secretary", false);
        homeroomTeacher = seedUser("Teacher", false);
        courseId = seedCourse(homeroomTeacher, "A");
        // Stable surnames: the report is ordered by last name, so generated ones would swap places.
        anaEnrollment = seedEnrollment(seedStudent("Ana", "Aguilar"), courseId);
        luisEnrollment = seedEnrollment(seedStudent("Luis", "Zambrana"), courseId);
    }

    private void seedDaily(UUID enrollmentId, LocalDate date, String status) {
        jdbc.update(
                "INSERT INTO attendance (id_attendance, id_course_enrollment, id_class_group, date, status) "
                        + "VALUES (?,?,NULL,?,?)",
                UUID.randomUUID(),
                enrollmentId,
                date,
                status);
    }

    private JsonNode report(UUID actor, String role, String query) throws Exception {
        String body =
                mvc.perform(
                                get("/api/courses/{id}/attendance-by-student" + query, courseId)
                                        .header("Authorization", "Bearer " + tokenFor(actor, role)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }

    private static JsonNode rowOf(JsonNode report, String studentName) {
        for (JsonNode row : report.get("students").get("content")) {
            if (row.get("studentName").asText().equals(studentName)) {
                return row;
            }
        }
        throw new AssertionError("No row for " + studentName);
    }

    @Test
    void countsAndPercentageArePerStudent() throws Exception {
        seedDaily(anaEnrollment, LocalDate.of(2026, 3, 2), "Present");
        seedDaily(anaEnrollment, LocalDate.of(2026, 3, 3), "Present");
        seedDaily(anaEnrollment, LocalDate.of(2026, 3, 4), "Late");
        seedDaily(anaEnrollment, LocalDate.of(2026, 3, 5), "Excused");
        seedDaily(luisEnrollment, LocalDate.of(2026, 3, 2), "Absent");
        seedDaily(luisEnrollment, LocalDate.of(2026, 3, 3), "Present");

        JsonNode node = report(director, "Director", "?trimester=1&limit=200");

        JsonNode ana = rowOf(node, "Ana Aguilar");
        assertThat(ana.get("present").asLong()).isEqualTo(2);
        assertThat(ana.get("late").asLong()).isEqualTo(1);
        assertThat(ana.get("excused").asLong()).isEqualTo(1);
        assertThat(ana.get("computableSessions").asLong()).isEqualTo(3);
        // 2 of 3 computable days; Excused is out of the calculation entirely.
        assertThat(ana.get("percentage").asDouble()).isEqualTo(66.7);

        JsonNode luis = rowOf(node, "Luis Zambrana");
        assertThat(luis.get("present").asLong()).isEqualTo(1);
        assertThat(luis.get("absent").asLong()).isEqualTo(1);
        assertThat(luis.get("percentage").asDouble()).isEqualTo(50.0);
    }

    /**
     * The invariant the two reports are built on: every student's counts summed give back the
     * classroom's. They read the same rows through the same period filter, and this is what catches
     * it if one of them ever stops doing that.
     */
    @Test
    void perStudentTotalsReconcileWithTheCoursePanel() throws Exception {
        seedDaily(anaEnrollment, LocalDate.of(2026, 3, 2), "Present");
        seedDaily(anaEnrollment, LocalDate.of(2026, 7, 2), "Absent");
        seedDaily(luisEnrollment, LocalDate.of(2026, 3, 2), "Late");
        seedDaily(luisEnrollment, LocalDate.of(2026, 9, 2), "Excused");
        // Outside every configured period: must be dropped by both.
        seedDaily(anaEnrollment, LocalDate.of(2026, 12, 20), "Absent");

        JsonNode byStudent = report(director, "Director", "?limit=200");
        long present = 0;
        long absent = 0;
        long late = 0;
        long excused = 0;
        for (JsonNode row : byStudent.get("students").get("content")) {
            present += row.get("present").asLong();
            absent += row.get("absent").asLong();
            late += row.get("late").asLong();
            excused += row.get("excused").asLong();
        }

        String panelBody =
                mvc.perform(
                                get("/api/courses/{id}/attendance-stats", courseId)
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode overall = json.readTree(panelBody).get("overall");

        assertThat(present).isEqualTo(overall.get("present").asLong());
        assertThat(absent).isEqualTo(overall.get("absent").asLong());
        assertThat(late).isEqualTo(overall.get("late").asLong());
        assertThat(excused).isEqualTo(overall.get("excused").asLong());
    }

    /** Session rows belong to one subject, not to the school day the regularity report is about. */
    @Test
    void sessionAttendanceIsExcluded() throws Exception {
        UUID classGroupId = seedClassGroup(courseId, seedUser("Teacher", false), "Matematicas");
        seedDaily(anaEnrollment, LocalDate.of(2026, 3, 2), "Present");
        jdbc.update(
                "INSERT INTO attendance (id_attendance, id_course_enrollment, id_class_group, date, status) "
                        + "VALUES (?,?,?,?,?)",
                UUID.randomUUID(),
                anaEnrollment,
                classGroupId,
                LocalDate.of(2026, 3, 3),
                "Absent");

        JsonNode ana = rowOf(report(director, "Director", "?trimester=1&limit=200"), "Ana Aguilar");

        assertThat(ana.get("computableSessions").asLong()).isEqualTo(1);
        assertThat(ana.get("percentage").asDouble()).isEqualTo(100.0);
    }

    /** Nobody marked them. That is a fact the report has to show, not a reason to omit the row. */
    @Test
    void aStudentWithNoMarksStillAppears() throws Exception {
        seedDaily(anaEnrollment, LocalDate.of(2026, 3, 2), "Present");

        JsonNode luis =
                rowOf(report(director, "Director", "?trimester=1&limit=200"), "Luis Zambrana");

        assertThat(luis.get("computableSessions").asLong()).isZero();
        assertThat(luis.get("percentage").isNull()).isTrue();
    }

    /** RF 37 names the Secretario alongside the Director. */
    @Test
    void secretaryReadsTheReport() throws Exception {
        seedDaily(anaEnrollment, LocalDate.of(2026, 3, 2), "Present");

        JsonNode node = report(secretary, "Secretary", "?trimester=1&limit=200");

        assertThat(node.get("students").get("content")).hasSize(2);
    }

    /** A teacher reaches their own course's report; the homeroom rule behind it is unchanged. */
    @Test
    void homeroomTeacherReadsTheirOwnCourse() throws Exception {
        seedDaily(anaEnrollment, LocalDate.of(2026, 3, 2), "Present");

        JsonNode node = report(homeroomTeacher, "Teacher", "?trimester=1&limit=200");

        assertThat(node.get("scope").asText()).isEqualTo("trimester");
    }

    @Test
    void anotherTeachersCourse_isRefused() throws Exception {
        UUID stranger = seedUser("Teacher", false);

        mvc.perform(
                        get("/api/courses/{id}/attendance-by-student", courseId)
                                .header("Authorization", "Bearer " + tokenFor(stranger, "Teacher")))
                .andExpect(status().isForbidden());
    }
}
