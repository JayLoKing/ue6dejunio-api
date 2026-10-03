package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.NewRiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskFeatures;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskLevel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionDomain;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What a teacher who runs one subject of a course reads about a student of it: the two per-student
 * reads that used to stop at the homeroom door ({@code student-summary}) or hand out every subject
 * ({@code /students/{id}/risk}), now narrowed to the subjects the reader teaches.
 */
class StudentScopedReadIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private IRiskPredictionDomain riskPredictions;

    private UUID homeroom;
    private UUID technical;
    private UUID stranger;
    private UUID director;
    private UUID secretary;
    private UUID courseId;
    private UUID mathGroup;
    private UUID languageGroup;
    private UUID ana;
    private UUID enrollment;

    @BeforeEach
    void seed() {
        homeroom = seedUser("Teacher", false);
        technical = seedUser("Teacher", true);
        stranger = seedUser("Teacher", false);
        director = seedUser("Director", false);
        secretary = seedUser("Secretary", false);
        courseId = seedCourse(homeroom, "A");
        mathGroup = seedClassGroup(courseId, homeroom, "Matematicas");
        languageGroup = seedClassGroup(courseId, technical, "Lenguaje");
        ana = seedStudent("Ana", "Alvarez");
        enrollment = seedEnrollment(ana, courseId);
        grade(mathGroup, 10, 40, 35, 5); // 90
        grade(languageGroup, 5, 20, 15, 5); // 45
        predict(mathGroup);
        predict(languageGroup);
    }

    private void grade(UUID classGroupId, int being, int knowing, int doing, int deciding) {
        jdbc.update(
                "INSERT INTO academic_scores (id_academic_score, id_course_enrollment, "
                        + "id_class_group, trimester, score_being, score_knowing, score_doing, "
                        + "score_deciding) VALUES (?,?,?,1,?,?,?,?)",
                UUID.randomUUID(),
                enrollment,
                classGroupId,
                BigDecimal.valueOf(being),
                BigDecimal.valueOf(knowing),
                BigDecimal.valueOf(doing),
                BigDecimal.valueOf(deciding));
    }

    private void predict(UUID classGroupId) {
        RiskFeatures vector =
                new RiskFeatures(
                        ana,
                        classGroupId,
                        1,
                        List.of(new BigDecimal("8")),
                        List.of(new BigDecimal("30")),
                        List.of(new BigDecimal("25")),
                        List.of(new BigDecimal("4")),
                        new BigDecimal("87.50"),
                        8);
        riskPredictions.upsertAll(
                List.of(
                        new NewRiskPrediction(
                                ana,
                                classGroupId,
                                1,
                                RiskLevel.EN_RIESGO,
                                new BigDecimal("0.5000"),
                                new BigDecimal("0.0100"),
                                vector,
                                LocalDateTime.of(2026, 4, 10, 8, 0))));
    }

    private String bearer(UUID userId, String role) {
        return "Bearer " + tokenFor(userId, role);
    }

    private org.springframework.test.web.servlet.ResultActions summaryAs(UUID userId, String role)
            throws Exception {
        return mvc.perform(
                get("/api/gradebook/student-summary")
                        .header("Authorization", bearer(userId, role))
                        .param("id_course_enrollment", enrollment.toString())
                        .param("trimester", "1"));
    }

    private org.springframework.test.web.servlet.ResultActions riskAs(UUID userId, String role)
            throws Exception {
        return mvc.perform(
                get("/api/students/{id}/risk", ana).header("Authorization", bearer(userId, role)));
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result)
            throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    // ---------------------------------------------------------------- student-summary

    @Test
    void summary_aTechnicalTeacher_readsOnlyTheirSubject_withTheSchoolsGeneralAverage()
            throws Exception {
        JsonNode full = body(summaryAs(director, "Director").andExpect(status().isOk()));
        JsonNode scoped = body(summaryAs(technical, "Teacher").andExpect(status().isOk()));

        assertThat(full.get("subjects")).hasSize(2);
        assertThat(scoped.get("subjects")).hasSize(1);
        assertThat(scoped.get("subjects").get(0).get("subjectName").asText()).isEqualTo("Lenguaje");
        assertThat(scoped.get("subjects").get(0).get("total").decimalValue())
                .isEqualByComparingTo("45");
        // The mean of 90 and 45, not of the one subject they teach.
        assertThat(scoped.get("generalAverage").decimalValue())
                .isEqualByComparingTo(full.get("generalAverage").decimalValue());
        assertThat(scoped.get("generalAverage").decimalValue()).isEqualByComparingTo("67.50");
    }

    @Test
    void summary_homeroomDirectorAndSecretary_readEverySubject() throws Exception {
        summaryAs(homeroom, "Teacher")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects.length()").value(2));
        summaryAs(director, "Director")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects.length()").value(2));
        summaryAs(secretary, "Secretary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects.length()").value(2));
    }

    @Test
    void summary_aTeacherWhoTeachesNothingInTheCourse_isRefused() throws Exception {
        summaryAs(stranger, "Teacher").andExpect(status().isForbidden());
    }

    /** The guard was opened for this one route; every sibling stays homeroom-or-Director. */
    @Test
    void siblingReads_stayClosedToTheTechnicalTeacher() throws Exception {
        mvc.perform(
                        get("/api/gradebook/report-card")
                                .header("Authorization", bearer(technical, "Teacher"))
                                .param("id_course_enrollment", enrollment.toString()))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/gradebook/centralizer")
                                .header("Authorization", bearer(technical, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/gradebook/annual-centralizer")
                                .header("Authorization", bearer(technical, "Teacher"))
                                .param("id_course", courseId.toString()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- /students/{id}/risk

    @Test
    void studentRisk_aTechnicalTeacher_readsOnlyTheirSubjects() throws Exception {
        riskAs(technical, "Teacher")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subjectName").value("Lenguaje"));
    }

    @Test
    void studentRisk_homeroomDirectorAndSecretary_readEverySubject() throws Exception {
        riskAs(homeroom, "Teacher").andExpect(jsonPath("$.length()").value(2));
        riskAs(director, "Director").andExpect(jsonPath("$.length()").value(2));
        riskAs(secretary, "Secretary").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void studentRisk_aTeacherUnrelatedToTheStudent_isRefused() throws Exception {
        riskAs(stranger, "Teacher").andExpect(status().isForbidden());
    }

    /**
     * A course between homeroom teachers carries a null there. The readable-groups query reaches
     * through that column, and a join on it would silently drop the course, and with it the subject
     * the teacher runs.
     */
    @Test
    void studentRisk_aSubjectTeacherInACourseWithNoHomeroom_stillReadsTheirSubject()
            throws Exception {
        UUID courseB = seedCourse(null, "B");
        UUID languageInB = seedClassGroup(courseB, technical, "Lenguaje");
        UUID mathInB = seedClassGroup(courseB, stranger, "Matematicas");
        seedEnrollment(ana, courseB);
        predict(languageInB);
        predict(mathInB);

        // Ana's rows: Matematicas and Lenguaje in A, Lenguaje and Matematicas in B. The technical
        // teacher reads Lenguaje of A and Lenguaje of B.
        riskAs(technical, "Teacher")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    /**
     * Homeroom of one of the student's courses and a subject teacher in another: all rows of the
     * first, only their own of the second.
     */
    @Test
    void studentRisk_homeroomOfOneCourseAndSubjectTeacherInAnother_readsEachByItsOwnRule()
            throws Exception {
        UUID courseB = seedCourse(technical, "B");
        UUID mathInB = seedClassGroup(courseB, homeroom, "Matematicas");
        seedEnrollment(ana, courseB);
        predict(mathInB);

        // `technical` runs Lenguaje in A and is the homeroom teacher of B.
        riskAs(technical, "Teacher")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
