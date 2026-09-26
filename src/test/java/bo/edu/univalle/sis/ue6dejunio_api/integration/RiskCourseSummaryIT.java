package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.NewRiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskFeatures;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Risk counted per classroom, over a real Postgres.
 *
 * <p>The folding of a student's subjects into one band is unit-tested against mocked ports. What
 * only a database can answer is whether the count really spans every classroom of the gestión, and
 * whether the students the sweep never reached are counted against the actual roster — that number
 * comes from a query this test is the only thing that exercises.
 */
class RiskCourseSummaryIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private IRiskPredictionDomain riskPredictions;

    private static final RiskFeatures FEATURES =
            new RiskFeatures(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    1,
                    List.of(new BigDecimal("8")),
                    List.of(new BigDecimal("30")),
                    List.of(new BigDecimal("28")),
                    List.of(new BigDecimal("4")),
                    new BigDecimal("87.5"),
                    8);

    private UUID director;
    private UUID teacher;

    @BeforeEach
    void seedSchool() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);

        // Quinto A: Ana fails two subjects — one student in critical trouble, not two. Beto is
        // enrolled and never predicted.
        UUID courseA = seedCourse(teacher, "A");
        UUID mathA = seedClassGroup(courseA, teacher, "Matematicas");
        UUID langA = seedClassGroup(courseA, teacher, "Lenguaje");
        UUID ana = seedStudent("Ana", "Alvarez");
        seedEnrollment(ana, courseA);
        seedEnrollment(seedStudent("Beto", "Quispe"), courseA);

        // Quinto B: one student, safely predicted.
        UUID courseB = seedCourse(teacher, "B");
        UUID mathB = seedClassGroup(courseB, teacher, "Matematicas");
        UUID carla = seedStudent("Carla", "Cruz");
        seedEnrollment(carla, courseB);

        riskPredictions.upsertAll(
                List.of(
                        prediction(ana, mathA, RiskLevel.RIESGO_CRITICO, "0.9000"),
                        prediction(ana, langA, RiskLevel.RIESGO_CRITICO, "0.8000"),
                        prediction(carla, mathB, RiskLevel.SIN_RIESGO, "0.1000")));
    }

    private NewRiskPrediction prediction(
            UUID student, UUID classGroup, RiskLevel level, String pFail) {
        return new NewRiskPrediction(
                student,
                classGroup,
                1,
                level,
                new BigDecimal(pFail),
                new BigDecimal("0.0100"),
                FEATURES,
                LocalDateTime.of(2026, 4, 10, 8, 0));
    }

    private String summaryAsDirector() throws Exception {
        return mvc.perform(
                        get("/api/risk/course-summary")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .param("id_academic_year", String.valueOf(currentAcademicYearId()))
                                .param("trimester", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void courseRiskSummary_reachesEveryClassroomOfTheGestion() throws Exception {
        String body = summaryAsDirector();

        assertThat(JsonPath.<List<String>>read(body, "$[*].parallelName"))
                .containsExactlyInAnyOrder("A", "B");
    }

    /**
     * The course is named {@code courseId}, camelCase, like every other response of this API.
     *
     * <p>The academic summary pins the same thing for the same reason: these two are read side by
     * side to build one table, and they are joined on this field. If one of them drifts the join
     * silently finds nothing and the risk columns come out empty.
     */
    @Test
    void courseRiskSummary_namesTheCourseInCamelCase() throws Exception {
        String body = summaryAsDirector();

        assertThat(JsonPath.<List<String>>read(body, "$[*].courseId")).hasSize(2);
    }

    /** Ana fails two areas and is one student in critical trouble. */
    @Test
    void courseRiskSummary_countsAStudentOnceHoweverManySubjectsTheyFail() throws Exception {
        String body = summaryAsDirector();

        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.parallelName == 'A')].critical"))
                .containsExactly(1);
    }

    /**
     * And Beto, enrolled but never predicted, is counted apart.
     *
     * <p>This is the number that keeps an unswept classroom from reading as a safe one: without it
     * Quinto A reports one student, in a room of two.
     */
    @Test
    void courseRiskSummary_countsTheEnrolledStudentsTheSweepNeverReached() throws Exception {
        String body = summaryAsDirector();

        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.parallelName == 'A')].unpredicted"))
                .containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.parallelName == 'B')].unpredicted"))
                .containsExactly(0);
        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.parallelName == 'B')].safe"))
                .containsExactly(1);
    }

    /** Whole-school scope, so Director only — the same rule the risk list carries. */
    @Test
    void courseRiskSummary_isClosedToATeacher() throws Exception {
        mvc.perform(
                        get("/api/risk/course-summary")
                                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                                .param("id_academic_year", String.valueOf(currentAcademicYearId()))
                                .param("trimester", "1"))
                .andExpect(status().isForbidden());
    }

    /** A trimester nobody was predicted in is every classroom with everybody unpredicted. */
    @Test
    void courseRiskSummary_aTrimesterWithoutPredictionsIsAllUnpredicted() throws Exception {
        String body =
                mvc.perform(
                                get("/api/risk/course-summary")
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director"))
                                        .param(
                                                "id_academic_year",
                                                String.valueOf(currentAcademicYearId()))
                                        .param("trimester", "3"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<List<Integer>>read(body, "$[*].critical")).containsOnly(0);
        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.parallelName == 'A')].unpredicted"))
                .containsExactly(2);
    }
}
