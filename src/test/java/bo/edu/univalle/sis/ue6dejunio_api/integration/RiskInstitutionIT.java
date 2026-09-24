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
 * The school-wide risk list over a real Postgres.
 *
 * <p>The ranking and the collapse to one row per student are unit-tested against mocked ports, and
 * those mocks hand back the rows the test itself wrote. What only a database can answer is whether
 * the list really walks every course of the gestión instead of reading one, and whether the
 * predictions come back named — the query behind them selects the student and the subject in the
 * same statement, and a panel handed bare uuids resolves them one row at a time.
 */
class RiskInstitutionIT extends AbstractIntegrationTest {

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

        UUID courseA = seedCourse(teacher, "A");
        UUID mathA = seedClassGroup(courseA, teacher, "Matematicas");
        UUID langA = seedClassGroup(courseA, teacher, "Lenguaje");
        UUID ana = seedStudent("Ana", "Alvarez");
        seedEnrollment(ana, courseA);

        // Another classroom of the same gestión, holding the worst prediction in the building.
        UUID courseB = seedCourse(teacher, "B");
        UUID mathB = seedClassGroup(courseB, teacher, "Matematicas");
        UUID carla = seedStudent("Carla", "Cruz");
        seedEnrollment(carla, courseB);

        riskPredictions.upsertAll(
                List.of(
                        // Two subjects for Ana: the list has to pick the worse of them and count
                        // her once.
                        prediction(ana, langA, RiskLevel.EN_RIESGO, "0.4000"),
                        prediction(ana, mathA, RiskLevel.RIESGO_CRITICO, "0.7000"),
                        prediction(carla, mathB, RiskLevel.RIESGO_CRITICO, "0.9500")));
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

    /**
     * The point of the whole-school scope: the worst of the building, not the worst of one room.
     * The worst prediction sits in course B, so a list that opens with course A's is reading one
     * course.
     */
    @Test
    void institutionRisk_reachesAcrossEveryCourseAndCountsAStudentOnce() throws Exception {
        String body =
                mvc.perform(
                                get("/api/risk/institution")
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director"))
                                        .param(
                                                "id_academic_year",
                                                String.valueOf(currentAcademicYearId()))
                                        .param("trimester", "1")
                                        .param("places", "10"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<List<String>>read(body, "$[*].fullName"))
                .containsExactly("Cruz Carla", "Alvarez Ana");
        assertThat(JsonPath.<List<String>>read(body, "$[*].parallelName"))
                .containsExactly("B", "A");
        assertThat(JsonPath.<List<Integer>>read(body, "$[*].position")).containsExactly(1, 2);
        // Ana sits two subjects and is predicted in both; the row she gets is her worse one.
        assertThat(JsonPath.<List<String>>read(body, "$[*].subjectName"))
                .containsExactly("Matematicas", "Matematicas");
        assertThat(JsonPath.<List<String>>read(body, "$[*].riskLevel"))
                .containsExactly("RiesgoCritico", "RiesgoCritico");
    }

    /**
     * The school's list is the Director's to read: a teacher owns their classroom, not the year.
     */
    @Test
    void institutionRisk_isClosedToATeacher() throws Exception {
        mvc.perform(
                        get("/api/risk/institution")
                                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                                .param("id_academic_year", String.valueOf(currentAcademicYearId()))
                                .param("trimester", "1"))
                .andExpect(status().isForbidden());
    }

    /** The trimester the list is asked for is the trimester it answers about, and nothing else. */
    @Test
    void institutionRisk_aTrimesterNobodyWasPredictedIn_isEmpty() throws Exception {
        String body =
                mvc.perform(
                                get("/api/risk/institution")
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

        assertThat(JsonPath.<List<String>>read(body, "$[*].fullName")).isEmpty();
    }
}
