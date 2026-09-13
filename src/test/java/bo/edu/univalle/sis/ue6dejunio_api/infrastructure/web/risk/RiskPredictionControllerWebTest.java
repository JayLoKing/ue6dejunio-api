package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.InstitutionRiskEntry;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionService.RunSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.RiskModelUnavailableException;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security.JwtAuthConverter;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The JSON the risk panel reads.
 *
 * <p>The shape is the contract with the web, and two things in it are easy to get quietly wrong:
 * the level has to be spelled the way the model and the column spell it, and the student and
 * subject have to arrive named. A panel handed {@code RIESGO_CRITICO} shows a constant nobody
 * writes, and a panel handed bare uuids fetches thirty times to draw thirty rows.
 *
 * <p>Security filters are off here, as in the other web tests: the role rules are exercised against
 * a real token elsewhere, and what is under test is the payload.
 */
@WebMvcTest(controllers = RiskPredictionController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({RiskPredictionControllerWebTest.MockBeans.class, GlobalExceptionHandler.class})
class RiskPredictionControllerWebTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private IRiskPredictionService predictionService;

    @TestConfiguration
    static class MockBeans {
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean JwtAuthConverter jwtAuthConverter() { return new JwtAuthConverter(); }
    }

    private static final UUID CLASS_GROUP = UUID.randomUUID();
    private static final UUID COURSE = UUID.randomUUID();
    private static final UUID STUDENT = UUID.randomUUID();

    private static StudentRisk risk(RiskLevel level) {
        RiskPrediction prediction = new RiskPrediction(
            UUID.randomUUID(), STUDENT, CLASS_GROUP, 1, level,
            new BigDecimal("0.8123"), new BigDecimal("0.0044"), false, "{}",
            LocalDateTime.of(2026, 4, 10, 8, 0));
        return new StudentRisk(prediction, "Ana", "Alvarez", "Matematicas");
    }

    private static InstitutionRiskEntry institutionEntry() {
        return new InstitutionRiskEntry(1, UUID.randomUUID(), STUDENT, "Alvarez Ana",
            COURSE, "Quinto", "B", CLASS_GROUP, "Matematicas",
            RiskLevel.RIESGO_CRITICO, new BigDecimal("0.9100"), false);
    }

    @Test
    void byClassGroup_namesTheStudentAndTheSubject() throws Exception {
        when(predictionService.byClassGroup(eq(CLASS_GROUP), anyInt()))
            .thenReturn(List.of(risk(RiskLevel.RIESGO_CRITICO)));

        mvc.perform(get("/api/class-groups/{id}/risk", CLASS_GROUP).param("trimester", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].studentName").value("Alvarez Ana"))
            .andExpect(jsonPath("$[0].subjectName").value("Matematicas"))
            .andExpect(jsonPath("$[0].studentId").value(STUDENT.toString()))
            .andExpect(jsonPath("$[0].classGroupId").value(CLASS_GROUP.toString()));
    }

    /**
     * The model's spelling, which is also the column's. Answering with the enum constant would give
     * the web a third vocabulary for the same four categories.
     */
    @Test
    void byClassGroup_spellsTheLevelTheWayTheModelAndTheColumnDo() throws Exception {
        when(predictionService.byClassGroup(eq(CLASS_GROUP), anyInt()))
            .thenReturn(List.of(risk(RiskLevel.RIESGO_CRITICO)));

        mvc.perform(get("/api/class-groups/{id}/risk", CLASS_GROUP).param("trimester", "1"))
            .andExpect(jsonPath("$[0].riskLevel").value("RiesgoCritico"));
    }

    /** Two probabilities, not one: about to fail and about to stand out are opposite questions. */
    @Test
    void byClassGroup_carriesBothProbabilities() throws Exception {
        when(predictionService.byClassGroup(eq(CLASS_GROUP), anyInt()))
            .thenReturn(List.of(risk(RiskLevel.RIESGO_CRITICO)));

        mvc.perform(get("/api/class-groups/{id}/risk", CLASS_GROUP).param("trimester", "1"))
            .andExpect(jsonPath("$[0].pFail").value(0.8123))
            .andExpect(jsonPath("$[0].pOutstanding").value(0.0044))
            .andExpect(jsonPath("$[0].attended").value(false));
    }

    @Test
    void byCourse_answersTheSameShape() throws Exception {
        when(predictionService.byCourse(eq(COURSE), anyInt()))
            .thenReturn(List.of(risk(RiskLevel.EN_RIESGO)));

        mvc.perform(get("/api/courses/{id}/risk", COURSE).param("trimester", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].riskLevel").value("EnRiesgo"))
            .andExpect(jsonPath("$[0].studentName").value("Alvarez Ana"));
    }

    @Test
    void byStudent_answersEverySubjectTheStudentSits() throws Exception {
        when(predictionService.byStudent(STUDENT)).thenReturn(List.of(risk(RiskLevel.SIN_RIESGO)));

        mvc.perform(get("/api/students/{id}/risk", STUDENT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].subjectName").value("Matematicas"));
    }

    /**
     * The Director's list. Its grain is one row per student, so unlike the panels above it carries
     * the classroom — a school-wide list of names with no classroom beside them names nobody.
     */
    @Test
    void institutionRisk_namesTheStudentTheirWorstSubjectAndTheirClassroom() throws Exception {
        when(predictionService.institutionRisk(eq(7), anyInt(), anyInt()))
            .thenReturn(List.of(institutionEntry()));

        mvc.perform(get("/api/risk/institution")
                .param("id_academic_year", "7")
                .param("trimester", "1")
                .param("places", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].position").value(1))
            .andExpect(jsonPath("$[0].fullName").value("Alvarez Ana"))
            .andExpect(jsonPath("$[0].subjectName").value("Matematicas"))
            .andExpect(jsonPath("$[0].gradeName").value("Quinto"))
            .andExpect(jsonPath("$[0].parallelName").value("B"))
            .andExpect(jsonPath("$[0].riskLevel").value("RiesgoCritico"))
            .andExpect(jsonPath("$[0].pFail").value(0.9100))
            .andExpect(jsonPath("$[0].attended").value(false));
    }

    /** The row has to be actionable: attending is per prediction, not per screen. */
    @Test
    void institutionRisk_carriesThePredictionTheRowStandsFor() throws Exception {
        InstitutionRiskEntry entry = institutionEntry();
        when(predictionService.institutionRisk(eq(7), anyInt(), anyInt()))
            .thenReturn(List.of(entry));

        mvc.perform(get("/api/risk/institution")
                .param("id_academic_year", "7")
                .param("trimester", "1"))
            .andExpect(jsonPath("$[0].predictionId").value(entry.predictionId().toString()))
            .andExpect(jsonPath("$[0].classGroupId").value(entry.classGroupId().toString()));
    }

    /** The gestión is what keeps the list inside one year. Without it there is no list to build. */
    @Test
    void institutionRisk_withoutAGestion_isRefused() throws Exception {
        mvc.perform(get("/api/risk/institution").param("trimester", "1"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void institutionRisk_aTrimesterThatDoesNotExist_isRefused() throws Exception {
        mvc.perform(get("/api/risk/institution")
                .param("id_academic_year", "7")
                .param("trimester", "4"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void predictClassGroup_answersWhatTheRunDid() throws Exception {
        when(predictionService.predictClassGroup(eq(CLASS_GROUP), anyInt()))
            .thenReturn(new RunSummary(30, 12, 18, 3));

        mvc.perform(post("/api/class-groups/{id}/risk/predict", CLASS_GROUP)
                .param("trimester", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.considered").value(30))
            .andExpect(jsonPath("$.skipped").value(12))
            .andExpect(jsonPath("$.predicted").value(18))
            .andExpect(jsonPath("$.changed").value(3));
    }

    /** There is no fourth trimester. Sent one, the query below would silently return nothing. */
    @Test
    void byClassGroup_aTrimesterThatDoesNotExist_isRefused() throws Exception {
        mvc.perform(get("/api/class-groups/{id}/risk", CLASS_GROUP).param("trimester", "4"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void byClassGroup_aTrimesterBelowTheFirst_isRefused() throws Exception {
        mvc.perform(get("/api/class-groups/{id}/risk", CLASS_GROUP).param("trimester", "0"))
            .andExpect(status().isBadRequest());
    }

    /**
     * 503, not 500. Nothing in this system is broken — another service is, and the difference is
     * what sends the Director to the right place.
     */
    @Test
    void predictClassGroup_theModelIsDown_answers503() throws Exception {
        when(predictionService.predictClassGroup(eq(CLASS_GROUP), anyInt()))
            .thenThrow(new RiskModelUnavailableException("Could not reach the model service."));

        mvc.perform(post("/api/class-groups/{id}/risk/predict", CLASS_GROUP)
                .param("trimester", "1"))
            .andExpect(status().isServiceUnavailable());
    }
}
