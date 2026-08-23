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
import java.util.regex.Pattern;

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
 * The first run bootstraps the golden file if missing and asserts against it once present, so the
 * second run proves identical output.
 *
 * <p>The comparison runs over a normalized copy: surrogate UUIDs are replaced by a placeholder,
 * because every seed mints fresh ones and a literal byte match would only ever succeed on the run
 * that wrote the file. Student names are seeded fixed for the same reason — the listing sorts by
 * last name, so generated names would reorder the rows between runs. Everything else is asserted
 * verbatim: field names and order, subject names, totals, averages, null-vs-value and paging.
 *
 * <p>The golden files must be committed; left untracked they regenerate on a clean checkout and
 * the test degrades into asserting the output against itself.
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

        // Fixed names: the centralizer sorts by last name, so random ones would swap the two rows
        // between runs and the captured output would never be stable. "Perez" sorts before
        // "Zapata", pinning the scored student to the first row.
        // Student with scores in both subjects, trimester 1.
        UUID studentWithScores = seedStudent("Ana", "Perez");
        UUID enrollmentWithScores = seedEnrollment(studentWithScores, courseId);
        // academic_scores caps each dimension: being<=10, knowing<=45, doing<=40, deciding<=5.
        seedAcademicScore(enrollmentWithScores, classGroupMath, 1,
            new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("25"), new BigDecimal("5"));
        seedAcademicScore(enrollmentWithScores, classGroupLang, 1,
            new BigDecimal("8"), new BigDecimal("15"), new BigDecimal("15"), new BigDecimal("4"));

        // Student enrolled but with zero recorded scores (empty-scores scenario).
        UUID studentNoScores = seedStudent("Luis", "Zapata");
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
        String normalized = normalizeIds(actual);
        if (Files.notExists(goldenPath)) {
            Files.createDirectories(goldenPath.getParent());
            Files.writeString(goldenPath, normalized, StandardCharsets.UTF_8);
            return;
        }
        String expected = Files.readString(goldenPath, StandardCharsets.UTF_8);
        assertThat(normalized).isEqualTo(expected);
    }

    /**
     * Blanks out the surrogate keys before comparing. Every seed mints fresh UUIDs, so a literal
     * byte comparison could only ever succeed on the run that wrote the golden. Everything that
     * actually characterizes the output — field names and order, student names, subject names,
     * totals, averages, null-vs-value, paging — stays under assertion.
     */
    private static String normalizeIds(String json) {
        return UUID_PATTERN.matcher(json).replaceAll("<uuid>");
    }

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
}
