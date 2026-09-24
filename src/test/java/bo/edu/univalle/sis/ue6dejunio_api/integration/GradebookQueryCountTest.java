package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec: Constant Query Count Per Page — GET /api/gradebook/centralizer must issue a bounded,
 * N-independent number of SQL statements per page.
 *
 * <p>Uses Hibernate {@link Statistics#getPrepareStatementCount()} (design decision: dependency-free
 * harness, {@code hibernate.generate_statistics=true} set in {@code application-it.properties}).
 * Resets stats before each request and asserts the prepared-statement count for a 1-student page
 * equals the count for a 50-student page — proving the batch-load rewrite removed the N+1.
 *
 * <p>Requires Docker/Testcontainers; not executable in the implementation sandbox for this change.
 * Because the sandbox could not run this test, "constant query count" is UNVERIFIED here — only
 * asserted by static code review of {@code GradebookService} (batch query moved outside the per-row
 * map/loop). See the apply-progress report.
 */
class GradebookQueryCountTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private UUID director;
    private UUID teacher;
    private UUID courseSmall;
    private UUID courseLarge;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        courseSmall = seedCourse(teacher, "A");
        courseLarge = seedCourse(teacher, "B");

        seedStudentsWithScores(courseSmall, 1);
        seedStudentsWithScores(courseLarge, 50);
    }

    private void seedStudentsWithScores(UUID courseId, int count) {
        UUID classGroup = seedClassGroup(courseId, teacher, "Matematicas");
        for (int i = 0; i < count; i++) {
            UUID student = seedStudent();
            UUID enrollment = seedEnrollment(student, courseId);
            jdbc.update(
                    "INSERT INTO academic_scores (id_academic_score, id_course_enrollment, id_class_group, "
                            + "trimester, score_being, score_knowing, score_doing, score_deciding) "
                            + "VALUES (?,?,?,?,?,?,?,?)",
                    // Each dimension has its own ceiling in academic_scores: being<=10,
                    // knowing<=45,
                    // doing<=40, deciding<=5.
                    UUID.randomUUID(),
                    enrollment,
                    classGroup,
                    1,
                    new BigDecimal("10"),
                    new BigDecimal("25"),
                    new BigDecimal("25"),
                    new BigDecimal("5"));
        }
    }

    @Test
    void centralizer_queryCountIsConstant_forOneVsFiftyStudents() throws Exception {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        stats.clear();
        mvc.perform(
                        get("/api/gradebook/centralizer")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .param("id_course", courseSmall.toString())
                                .param("trimester", "1")
                                .param("offset", "1")
                                .param("limit", "200"))
                .andExpect(status().isOk());
        long countForOneStudent = stats.getPrepareStatementCount();

        stats.clear();
        mvc.perform(
                        get("/api/gradebook/centralizer")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .param("id_course", courseLarge.toString())
                                .param("trimester", "1")
                                .param("offset", "1")
                                .param("limit", "200"))
                .andExpect(status().isOk());
        long countForFiftyStudents = stats.getPrepareStatementCount();

        assertThat(countForFiftyStudents)
                .as("prepared-statement count must not scale with page student count")
                .isEqualTo(countForOneStudent);
    }
}
