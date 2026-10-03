package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The libreta and the annual centralizer read a closed year's frozen result, over a real Postgres.
 *
 * <p>The frozen average is overwritten by hand after the close with a value no recomputation could
 * produce (and a result the average would contradict), so a response that carries them can only
 * have read the stored columns.
 */
class FrozenResultReadIT extends AbstractIntegrationTest {

    private static final String FROZEN_BY_HAND = "77.77";

    @Autowired private MockMvc mvc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private UUID director;
    private UUID teacher;
    private UUID course;
    private UUID classGroup;
    private UUID graded;
    private UUID ungraded;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        course = seedCourse(teacher, "A", academicYearId(2026));
        classGroup = seedClassGroup(course, teacher, "Lenguaje");
        graded = seedEnrollment(seedStudent("Ana", "Alvarez"), course);
        ungraded = seedEnrollment(seedStudent("Eva", "Estrada"), course);
        grade(graded, 60); // recomputed average: 60.00, which would be APPROVED
    }

    private void grade(UUID enrollment, int total) {
        for (int trimester = 1; trimester <= 3; trimester++) {
            jdbc.update(
                    "INSERT INTO academic_scores (id_academic_score, id_course_enrollment, "
                            + "id_class_group, trimester, score_being, score_knowing, score_doing, "
                            + "score_deciding) VALUES (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    enrollment,
                    classGroup,
                    trimester,
                    new BigDecimal("10"),
                    new BigDecimal("20"),
                    new BigDecimal(total - 35),
                    new BigDecimal("5"));
        }
    }

    private String bearer() {
        return "Bearer " + tokenFor(director, "Director");
    }

    private void changeYear(String action) throws Exception {
        mvc.perform(
                        put("/api/academic-years/" + academicYearId(2026) + "/" + action)
                                .header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    /** Closes the year, then replaces the frozen values of the graded student by hand. */
    private void closeAndOverwriteFrozenValues() throws Exception {
        changeYear("close");
        jdbc.update(
                "UPDATE course_enrollments SET final_average = ?, final_result = 'FAILED' "
                        + "WHERE id_course_enrollment = ?",
                new BigDecimal(FROZEN_BY_HAND),
                graded);
    }

    private String reportCard(UUID enrollment) throws Exception {
        return mvc.perform(
                        get("/api/gradebook/report-card")
                                .param("id_course_enrollment", enrollment.toString())
                                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private String annualCentralizer(UUID courseId) throws Exception {
        return mvc.perform(
                        get("/api/gradebook/annual-centralizer")
                                .param("id_course", courseId.toString())
                                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static Object field(String body, UUID enrollment, String name) {
        List<Object> found =
                JsonPath.read(
                        body, "$.content[?(@.courseEnrollmentId=='" + enrollment + "')]." + name);
        return found.get(0);
    }

    private static BigDecimal number(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    @Test
    void closedYear_reportCard_carriesTheFrozenAverageResultAndFlag() throws Exception {
        closeAndOverwriteFrozenValues();

        String body = reportCard(graded);

        assertThat(number(JsonPath.read(body, "$.finalAverage")))
                .isEqualByComparingTo(FROZEN_BY_HAND);
        assertThat((String) JsonPath.read(body, "$.finalResult")).isEqualTo("FAILED");
        assertThat((Boolean) JsonPath.read(body, "$.frozen")).isTrue();
        // Still the recomputed trimester averages: only the final one is frozen.
        assertThat(number(JsonPath.read(body, "$.trimesterAverages[0]")))
                .isEqualByComparingTo("60.00");
    }

    @Test
    void closedYear_reportCard_ofAnUngradedStudent_hasNoResult_butIsFrozen() throws Exception {
        closeAndOverwriteFrozenValues();

        String body = reportCard(ungraded);

        assertThat((Object) JsonPath.read(body, "$.finalAverage")).isNull();
        assertThat((Object) JsonPath.read(body, "$.finalResult")).isNull();
        assertThat((Boolean) JsonPath.read(body, "$.frozen")).isTrue();
    }

    @Test
    void closedYear_annualCentralizer_carriesTheFrozenAverageResultAndFlagPerRow()
            throws Exception {
        closeAndOverwriteFrozenValues();

        String body = annualCentralizer(course);

        assertThat(number(field(body, graded, "finalAverage")))
                .isEqualByComparingTo(FROZEN_BY_HAND);
        assertThat(field(body, graded, "finalResult")).isEqualTo("FAILED");
        assertThat(field(body, graded, "frozen")).isEqualTo(true);
        assertThat(field(body, ungraded, "finalAverage")).isNull();
        assertThat(field(body, ungraded, "finalResult")).isNull();
        assertThat(field(body, ungraded, "frozen")).isEqualTo(true);
    }

    @Test
    void reopenedYear_goesBackToTheRecomputedAverage_andShowsNoResult() throws Exception {
        closeAndOverwriteFrozenValues();
        changeYear("reopen");

        String card = reportCard(graded);
        String sheet = annualCentralizer(course);

        // The frozen columns still hold 77.77 and FAILED; an open year must not read them.
        assertThat(number(JsonPath.read(card, "$.finalAverage"))).isEqualByComparingTo("60.00");
        assertThat((Object) JsonPath.read(card, "$.finalResult")).isNull();
        assertThat((Boolean) JsonPath.read(card, "$.frozen")).isFalse();
        assertThat(number(field(sheet, graded, "finalAverage"))).isEqualByComparingTo("60.00");
        assertThat(field(sheet, graded, "finalResult")).isNull();
        assertThat(field(sheet, graded, "frozen")).isEqualTo(false);
    }

    @Test
    void openYear_neverFrozen_behavesAsBefore() throws Exception {
        String card = reportCard(graded);

        assertThat(number(JsonPath.read(card, "$.finalAverage"))).isEqualByComparingTo("60.00");
        assertThat((Object) JsonPath.read(card, "$.finalResult")).isNull();
        assertThat((Boolean) JsonPath.read(card, "$.frozen")).isFalse();
    }

    @Test
    void closedYear_annualCentralizer_statementCountDoesNotScaleWithTheRoster() throws Exception {
        UUID big = seedCourse(seedUser("Teacher", false), "B", academicYearId(2026));
        for (int i = 0; i < 25; i++) {
            seedEnrollment(seedStudent(), big);
        }
        changeYear("close");
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        stats.clear();
        annualCentralizer(course);
        long small = stats.getPrepareStatementCount();

        stats.clear();
        annualCentralizer(big);
        long large = stats.getPrepareStatementCount();

        assertThat(large).as("prepared statements per annual centralizer page").isEqualTo(small);
    }
}
