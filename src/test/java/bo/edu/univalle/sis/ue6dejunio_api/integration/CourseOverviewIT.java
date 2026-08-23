package bo.edu.univalle.sis.ue6dejunio_api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec: director-course-overview — GET /api/courses/{id}/overview.
 *
 * <p>Covers: full composite shape, pagination honored, N+1-safe student/grade loading (constant
 * query count via batch centralizer path), empty course, invalid course id (404), and course-scoped
 * authorization (Director/homeroom 200, non-owner incl. technical 403, unauthenticated 401).
 *
 * <p>Requires Docker/Testcontainers; skipped ({@code disabledWithoutDocker}) in this sandbox.
 */
class CourseOverviewIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private UUID director;
    private UUID homeroomTeacher;
    private UUID otherTeacher;
    private UUID technicalTeacher;
    private UUID courseA;
    private UUID classGroupMath;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        homeroomTeacher = seedUser("Teacher", false);
        otherTeacher = seedUser("Teacher", false);
        technicalTeacher = seedUser("Teacher", true);
        courseA = seedCourse(homeroomTeacher, "A");
        classGroupMath = seedClassGroup(courseA, homeroomTeacher, "Matematicas");
    }

    private void seedStudentWithScore(UUID courseId, UUID classGroupId, BigDecimal total) {
        UUID student = seedStudent();
        UUID enrollment = seedEnrollment(student, courseId);
        jdbc.update(
            "INSERT INTO academic_scores (id_academic_score, id_course_enrollment, id_class_group, "
                + "trimester, score_being, score_knowing, score_doing, score_deciding) "
                + "VALUES (?,?,?,?,?,?,?,?)",
            // Each dimension has its own ceiling in academic_scores (being<=10, knowing<=45,
            // doing<=40, deciding<=5). Pin three of them at their cap and let "doing" absorb the
            // requested total, which stays inside its own ceiling for every total used here.
            UUID.randomUUID(), enrollment, classGroupId, 1,
            new BigDecimal("10"), new BigDecimal("45"), total.subtract(new BigDecimal("60")),
            new BigDecimal("5"));
    }

    @Test
    void fullComposite_returned_forSeededCourse() throws Exception {
        seedStudentWithScore(courseA, classGroupMath, new BigDecimal("90.00"));

        String body = mvc.perform(get("/api/courses/{id}/overview", courseA)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("trimester", "1")
                .param("offset", "1")
                .param("limit", "30"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode root = json.readTree(body);
        assertThat(root.get("course").get("id").asText()).isEqualTo(courseA.toString());
        assertThat(root.get("course").get("homeroomTeacherId").asText()).isEqualTo(homeroomTeacher.toString());
        assertThat(root.get("classGroups")).hasSize(1);
        assertThat(root.get("classGroups").get(0).get("subjectName").asText()).isEqualTo("Matematicas");
        assertThat(root.get("students").get("content")).hasSize(1);
        assertThat(root.get("students").get("total").asLong()).isEqualTo(1);
    }

    @Test
    void pagination_isHonored() throws Exception {
        for (int i = 0; i < 5; i++) {
            seedStudentWithScore(courseA, classGroupMath, new BigDecimal("80.00"));
        }

        String body = mvc.perform(get("/api/courses/{id}/overview", courseA)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("trimester", "1")
                .param("offset", "1")
                .param("limit", "2"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode root = json.readTree(body);
        assertThat(root.get("students").get("content")).hasSize(2);
        assertThat(root.get("students").get("total").asLong()).isEqualTo(5);
        assertThat(root.get("students").get("size").asInt()).isEqualTo(2);
    }

    @Test
    void studentGradeLoading_isN1Safe_queryCountConstant() throws Exception {
        UUID courseLarge = seedCourse(homeroomTeacher, "B");
        UUID classGroupLarge = seedClassGroup(courseLarge, homeroomTeacher, "Lenguaje");
        seedStudentWithScore(courseA, classGroupMath, new BigDecimal("90.00"));
        for (int i = 0; i < 50; i++) {
            seedStudentWithScore(courseLarge, classGroupLarge, new BigDecimal("70.00"));
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        stats.clear();
        mvc.perform(get("/api/courses/{id}/overview", courseA)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("trimester", "1")
                .param("offset", "1")
                .param("limit", "200"))
            .andExpect(status().isOk());
        long countForOneStudent = stats.getPrepareStatementCount();

        stats.clear();
        mvc.perform(get("/api/courses/{id}/overview", courseLarge)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("trimester", "1")
                .param("offset", "1")
                .param("limit", "200"))
            .andExpect(status().isOk());
        long countForFiftyStudents = stats.getPrepareStatementCount();

        assertThat(countForFiftyStudents)
            .as("grade-fetching query count must not scale with student count (batch centralizer path)")
            .isEqualTo(countForOneStudent);
    }

    @Test
    void emptyCourse_returnsHeaderWithEmptyPage() throws Exception {
        String body = mvc.perform(get("/api/courses/{id}/overview", courseA)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("trimester", "1"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode root = json.readTree(body);
        assertThat(root.get("students").get("content")).isEmpty();
        assertThat(root.get("students").get("total").asLong()).isZero();
    }

    @Test
    void invalidCourseId_returns404() throws Exception {
        mvc.perform(get("/api/courses/{id}/overview", UUID.randomUUID())
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("trimester", "1"))
            .andExpect(status().isNotFound());
    }

    @Test
    void directorAccess_ok() throws Exception {
        mvc.perform(get("/api/courses/{id}/overview", courseA)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("trimester", "1"))
            .andExpect(status().isOk());
    }

    @Test
    void homeroomTeacherAccess_ok() throws Exception {
        mvc.perform(get("/api/courses/{id}/overview", courseA)
                .header("Authorization", "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                .param("trimester", "1"))
            .andExpect(status().isOk());
    }

    @Test
    void nonOwnerTeacher_rejected_forbidden() throws Exception {
        mvc.perform(get("/api/courses/{id}/overview", courseA)
                .header("Authorization", "Bearer " + tokenFor(otherTeacher, "Teacher"))
                .param("trimester", "1"))
            .andExpect(status().isForbidden());
    }

    @Test
    void technicalTeacher_neverHomeroom_rejected_forbidden() throws Exception {
        mvc.perform(get("/api/courses/{id}/overview", courseA)
                .header("Authorization", "Bearer " + tokenFor(technicalTeacher, "Teacher"))
                .param("trimester", "1"))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_rejected_401() throws Exception {
        mvc.perform(get("/api/courses/{id}/overview", courseA)
                .param("trimester", "1"))
            .andExpect(status().isUnauthorized());
    }
}
