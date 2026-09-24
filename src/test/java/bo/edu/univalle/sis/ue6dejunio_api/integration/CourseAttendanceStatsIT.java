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
 * Spec B: GET /api/courses/{id}/attendance-stats — exercises the real single-query GROUP BY
 * aggregation (JpaAttendanceRepository#dailyStatusCountsByCourseGroupedByDate) against Postgres,
 * bucketed via the Director-configured {@code academic_trimesters} periods seeded for academic year
 * 2026 in schema-it.sql (T1 Feb-May, T2 Jun-Aug, T3 Sep-Nov), and the gender join for GET
 * /api/course-enrollments (Spec A). Requires Docker/Testcontainers; SKIPPED in this environment
 * (Docker unavailable) — see apply-progress report.
 */
class CourseAttendanceStatsIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID homeroomTeacher;
    private UUID courseId;
    private UUID enrollmentId;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        homeroomTeacher = seedUser("Teacher", false);
        courseId = seedCourse(homeroomTeacher, "A");
        UUID studentId = seedStudent();
        enrollmentId = seedEnrollment(studentId, courseId);
    }

    private void seedDailyAttendance(LocalDate date, String status) {
        jdbc.update(
                "INSERT INTO attendance (id_attendance, id_course_enrollment, id_class_group, date, status) "
                        + "VALUES (?,?,NULL,?,?)",
                UUID.randomUUID(),
                enrollmentId,
                date,
                status);
    }

    private void seedSessionAttendance(UUID classGroupId, LocalDate date, String status) {
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
    void annualScope_aggregatesAcrossMonthsAndTrimesters_singleGroupByQuery() throws Exception {
        seedDailyAttendance(LocalDate.of(2026, 3, 1), "Present");
        seedDailyAttendance(LocalDate.of(2026, 3, 2), "Absent");
        seedDailyAttendance(LocalDate.of(2026, 7, 1), "Present");
        seedDailyAttendance(LocalDate.of(2026, 7, 2), "Excused");

        String body =
                mvc.perform(
                                get("/api/courses/{id}/attendance-stats", courseId)
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode node = json.readTree(body);
        assertThat(node.get("scope").asText()).isEqualTo("annual");
        assertThat(node.get("byMonth")).hasSize(2);
        assertThat(node.get("byTrimester")).hasSize(2);
    }

    @Test
    void sessionAttendance_excludedFromStats() throws Exception {
        UUID classGroupId = seedClassGroup(courseId, seedUser("Teacher", false), "Matematicas");
        seedDailyAttendance(LocalDate.of(2026, 3, 1), "Present");
        seedSessionAttendance(classGroupId, LocalDate.of(2026, 3, 1), "Absent");

        String body =
                mvc.perform(
                                get("/api/courses/{id}/attendance-stats", courseId)
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode overall = json.readTree(body).get("overall");
        assertThat(overall.get("present").asLong()).isEqualTo(1);
        assertThat(overall.get("absent").asLong()).isZero();
    }

    @Test
    void trimesterScope_filtersToRequestedTrimester() throws Exception {
        seedDailyAttendance(LocalDate.of(2026, 3, 1), "Present");
        seedDailyAttendance(LocalDate.of(2026, 7, 1), "Absent");

        String body =
                mvc.perform(
                                get("/api/courses/{id}/attendance-stats", courseId)
                                        .param("trimester", "1")
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode node = json.readTree(body);
        assertThat(node.get("scope").asText()).isEqualTo("trimester");
        assertThat(node.get("byMonth")).hasSize(1);
        assertThat(node.get("overall").get("present").asLong()).isEqualTo(1);
    }

    @Test
    void nonHomeroomTeacher_forbidden() throws Exception {
        UUID otherTeacher = seedUser("Teacher", false);

        mvc.perform(
                        get("/api/courses/{id}/attendance-stats", courseId)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(otherTeacher, "Teacher")))
                .andExpect(status().isForbidden());
    }

    /**
     * GGA reachability fix: SecurityConfig maps GET /api/courses/*&#47;attendance-stats to
     * hasAnyRole("Director","Teacher") BEFORE the general /api/courses/** Director-only rule, so
     * the @PreAuthorize("@authz.canReadCourse(...)") guard on CourseController can actually grant
     * the homeroom Teacher (previously rejected at the filter chain before @PreAuthorize ran).
     */
    @Test
    void homeroomTeacher_canReadAttendanceStats() throws Exception {
        mvc.perform(
                        get("/api/courses/{id}/attendance-stats", courseId)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher")))
                .andExpect(status().isOk());
    }

    @Test
    void courseStudentsList_includesGender() throws Exception {
        String body =
                mvc.perform(
                                get("/api/course-enrollments")
                                        .param("id_course", courseId.toString())
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode content = json.readTree(body).get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("gender").asText()).isEqualTo("M");
    }
}
