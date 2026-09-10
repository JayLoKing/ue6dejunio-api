package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.NewRiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskFeatures;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who actually reaches the risk panel, through the real filter chain.
 *
 * <p>Worth its own class because two independent layers have to agree: the role rules in
 * {@code SecurityConfig} decide who reaches the handler at all, and {@code @PreAuthorize} decides
 * whose rows they see. A route the chain does not name inherits whatever the nearest wildcard says
 * — {@code /api/courses/**} is Director-only — and the guard behind it never runs to disagree. That
 * failure is invisible to every test that mocks the service away.
 */
class RiskPredictionAuthorizationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private IRiskPredictionDomain riskPredictions;

    private UUID owningTeacher;
    private UUID otherTeacher;
    private UUID director;
    private UUID secretary;
    private UUID courseId;
    private UUID mathGroup;
    private UUID ana;
    private UUID predictionId;

    @BeforeEach
    void seed() {
        owningTeacher = seedUser("Teacher", false);
        otherTeacher = seedUser("Teacher", false);
        director = seedUser("Director", false);
        secretary = seedUser("Secretary", false);
        courseId = seedCourse(owningTeacher, "A");
        mathGroup = seedClassGroup(courseId, owningTeacher, "Matematicas");
        ana = seedStudent("Ana", "Alvarez");
        seedEnrollment(ana, courseId);

        RiskFeatures vector = new RiskFeatures(ana, mathGroup, 1,
            List.of(new BigDecimal("8")), List.of(new BigDecimal("30")),
            List.of(new BigDecimal("25")), List.of(new BigDecimal("4")),
            new BigDecimal("87.50"), 8);
        predictionId = riskPredictions.upsertAll(List.of(new NewRiskPrediction(
            ana, mathGroup, 1, RiskLevel.RIESGO_CRITICO,
            new BigDecimal("0.8100"), new BigDecimal("0.0044"), vector,
            LocalDateTime.of(2026, 4, 10, 8, 0)))).get(0).stored().id();
    }

    private String bearer(UUID userId, String role) {
        return "Bearer " + tokenFor(userId, role);
    }

    // ---------------------------------------------------------------- the course panel

    /**
     * The one the wildcard swallowed. {@code /api/courses/**} is Director-only, so without its own
     * rule this answers 403 to the teacher whose course it is — a panel written for nobody.
     */
    @Test
    void courseRisk_theTeacherOfTheCourse_reachesIt() throws Exception {
        mvc.perform(get("/api/courses/{id}/risk", courseId)
                .header("Authorization", bearer(owningTeacher, "Teacher"))
                .param("trimester", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].studentName").value("Alvarez Ana"))
            .andExpect(jsonPath("$[0].riskLevel").value("RiesgoCritico"));
    }

    /**
     * The reason this endpoint is guarded by the roster rule and not by {@code canReadCourse}: a
     * technical teacher has no homeroom by definition, and the panel spans every subject of the
     * course including the one they run. Gated on homeroom they would be refused a list about their
     * own students.
     */
    @Test
    void courseRisk_aTeacherWhoOnlyRunsOneSubjectOfTheCourse_reachesIt() throws Exception {
        UUID technicalTeacher = seedUser("Teacher", true);
        seedClassGroup(courseId, technicalTeacher, "Lenguaje");

        mvc.perform(get("/api/courses/{id}/risk", courseId)
                .header("Authorization", bearer(technicalTeacher, "Teacher"))
                .param("trimester", "1"))
            .andExpect(status().isOk());
    }

    @Test
    void courseRisk_theSecretariatReadsIt() throws Exception {
        mvc.perform(get("/api/courses/{id}/risk", courseId)
                .header("Authorization", bearer(secretary, "Secretary"))
                .param("trimester", "1"))
            .andExpect(status().isOk());
    }

    @Test
    void courseRisk_theDirectorReadsIt() throws Exception {
        mvc.perform(get("/api/courses/{id}/risk", courseId)
                .header("Authorization", bearer(director, "Director"))
                .param("trimester", "1"))
            .andExpect(status().isOk());
    }

    @Test
    void courseRisk_aTeacherWithNothingInTheCourse_isRefused() throws Exception {
        mvc.perform(get("/api/courses/{id}/risk", courseId)
                .header("Authorization", bearer(otherTeacher, "Teacher"))
                .param("trimester", "1"))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- the subject panel

    @Test
    void classGroupRisk_theTeacherOfTheSubject_reachesIt() throws Exception {
        mvc.perform(get("/api/class-groups/{id}/risk", mathGroup)
                .header("Authorization", bearer(owningTeacher, "Teacher"))
                .param("trimester", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].subjectName").value("Matematicas"));
    }

    @Test
    void classGroupRisk_anotherTeachersSubject_isRefused() throws Exception {
        mvc.perform(get("/api/class-groups/{id}/risk", mathGroup)
                .header("Authorization", bearer(otherTeacher, "Teacher"))
                .param("trimester", "1"))
            .andExpect(status().isForbidden());
    }

    /** Running the model is a write, and the secretariat writes nothing. */
    @Test
    void classGroupPredict_theSecretariat_isRefused() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/class-groups/{id}/risk/predict", mathGroup)
                .header("Authorization", bearer(secretary, "Secretary"))
                .param("trimester", "1"))
            .andExpect(status().isForbidden());
    }

    /** A sweep of the whole school is the Director's alone. */
    @Test
    void predictYear_aTeacher_isRefused() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/risk/predict-year")
                .header("Authorization", bearer(owningTeacher, "Teacher"))
                .param("academicYear", "2026")
                .param("trimester", "1"))
            .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- marking one as handled

    @Test
    void attend_theTeacherWhoseSubjectItIs_marksIt() throws Exception {
        mvc.perform(put("/api/risk-predictions/{id}/attend", predictionId)
                .header("Authorization", bearer(owningTeacher, "Teacher"))
                .param("attended", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attended").value(true));
    }

    /**
     * The write is a flag, but the answer carries the student, their category and their probability
     * of failing. Guarded by role alone, any teacher could walk the risk roster of the school by
     * trying ids.
     */
    @Test
    void attend_aTeacherFromAnotherCourse_isRefusedAndReadsNothing() throws Exception {
        mvc.perform(put("/api/risk-predictions/{id}/attend", predictionId)
                .header("Authorization", bearer(otherTeacher, "Teacher"))
                .param("attended", "true"))
            .andExpect(status().isForbidden());
    }

    @Test
    void attend_theSecretariat_isRefused() throws Exception {
        mvc.perform(put("/api/risk-predictions/{id}/attend", predictionId)
                .header("Authorization", bearer(secretary, "Secretary"))
                .param("attended", "true"))
            .andExpect(status().isForbidden());
    }

    /** An id that resolves to nothing denies from inside the guard rather than surfacing a 404. */
    @Test
    void attend_anIdThatIsNobodys_isRefused() throws Exception {
        mvc.perform(put("/api/risk-predictions/{id}/attend", UUID.randomUUID())
                .header("Authorization", bearer(otherTeacher, "Teacher"))
                .param("attended", "true"))
            .andExpect(status().isForbidden());
    }
}
