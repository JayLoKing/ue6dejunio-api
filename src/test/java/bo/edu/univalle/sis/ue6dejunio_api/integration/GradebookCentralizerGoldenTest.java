package bo.edu.univalle.sis.ue6dejunio_api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec: Output Equivalence (Centralizer) — GET /api/gradebook/centralizer.
 *
 * Characterization test: captures the byte-identical JSON produced by {@code
 * GradebookService.centralizer} for a seeded course/trimester/page, comparing it against a
 * committed golden resource. This MUST be run and its golden committed against the CURRENT
 * (unmodified) service BEFORE any batch-load refactor lands, per the strict-TDD ordering
 * constraint in this change.
 *
 * <p><b>Capture procedure</b> (requires Docker; run once, before refactor commit):
 * <pre>
 *   git stash push -- \
 *     src/main/java/.../application/services/gradebook/GradebookService.java \
 *     src/main/java/.../domain/ports/score/IScoreDomain.java \
 *     src/main/java/.../domain/ports/attendance/IAttendanceDomain.java \
 *     src/main/java/.../infrastructure/adapters/ScoreRepositoryAdapter.java \
 *     src/main/java/.../infrastructure/adapters/AttendanceRepositoryAdapter.java \
 *     src/main/java/.../infrastructure/repositories/JpaAcademicScoreRepository.java \
 *     src/main/java/.../infrastructure/repositories/JpaAttendanceRepository.java
 *   ./gradlew test -Dtest=GradebookCentralizerGoldenTest,GradebookAttendanceGoldenTest -x spotlessCheck
 *   git add src/test/resources/gradebook/golden/*.json
 *   git stash pop
 *   ./gradlew test -Dtest=GradebookCentralizerGoldenTest,GradebookAttendanceGoldenTest -x spotlessCheck
 * </pre>
 * The first run (pre-refactor code) bootstraps the golden file if missing and always asserts
 * against it once present, so the second run (post-refactor code, after {@code stash pop})
 * proves byte-identical output. This class was authored without Docker available in the
 * implementation sandbox, so the golden file has NOT been captured/committed yet — see the
 * apply-progress report for this change.
 */
class GradebookCentralizerGoldenTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private static final Path GOLDEN_PATH =
        Path.of("src/test/resources/gradebook/golden/centralizer-page1.json");

    private UUID director;
    private UUID courseId;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        UUID teacher = seedUser("Teacher", false);
        courseId = seedCourse(teacher, "A");

        UUID classGroupMath = seedClassGroup(courseId, teacher, "Matematicas");
        UUID classGroupLang = seedClassGroup(courseId, teacher, "Lenguaje");

        // Student with scores in both subjects, trimester 1.
        UUID studentWithScores = seedStudent();
        UUID enrollmentWithScores = seedEnrollment(studentWithScores, courseId);
        seedAcademicScore(enrollmentWithScores, classGroupMath, 1,
            new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("25"), new BigDecimal("25"));
        seedAcademicScore(enrollmentWithScores, classGroupLang, 1,
            new BigDecimal("15"), new BigDecimal("15"), new BigDecimal("15"), new BigDecimal("15"));

        // Student enrolled but with zero recorded scores (empty-scores scenario).
        UUID studentNoScores = seedStudent();
        seedEnrollment(studentNoScores, courseId);
    }

    private void seedAcademicScore(UUID enrollmentId, UUID classGroupId, int trimester,
                                   BigDecimal being, BigDecimal knowing, BigDecimal doing, BigDecimal deciding) {
        jdbc.update(
            "INSERT INTO academic_scores (id_academic_score, id_course_enrollment, id_class_group, trimester, "
                + "score_being, score_knowing, score_doing, score_deciding) VALUES (?,?,?,?,?,?,?,?)",
            UUID.randomUUID(), enrollmentId, classGroupId, trimester, being, knowing, doing, deciding);
    }

    @Test
    void centralizerPage1_matchesCommittedGolden() throws Exception {
        String actual = mvc.perform(get("/api/gradebook/centralizer")
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("id_course", courseId.toString())
                .param("trimester", "1")
                .param("offset", "1")
                .param("limit", "30"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertGoldenMatch(GOLDEN_PATH, actual);
    }

    /**
     * Bootstraps the golden file on first run (no committed golden yet) and asserts byte-identical
     * equality once a golden exists. See class javadoc for the required pre-refactor capture step.
     */
    static void assertGoldenMatch(Path goldenPath, String actual) throws IOException {
        if (Files.notExists(goldenPath)) {
            Files.createDirectories(goldenPath.getParent());
            Files.writeString(goldenPath, actual, StandardCharsets.UTF_8);
            return;
        }
        String expected = Files.readString(goldenPath, StandardCharsets.UTF_8);
        assertThat(actual).isEqualTo(expected);
    }
}
