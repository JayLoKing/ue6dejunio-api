package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec: Score write/read ownership guard + Resolver not-found handling. Endpoints:
 * AssessmentScoreController.setScore/delete/byEvent/byCourseEnrollment.
 */
class AssessmentScoreAuthorizationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID teacherA;
    private UUID teacherB;
    private UUID director;
    private UUID courseA;
    private UUID classGroupA;
    private UUID studentEnrollment;
    private UUID eventId;

    @BeforeEach
    void seed() {
        teacherA = seedUser("Teacher", false);
        teacherB = seedUser("Teacher", false);
        director = seedUser("Director", false);
        courseA = seedCourse(teacherA, "A");
        classGroupA = seedClassGroup(courseA, teacherA, "Matematicas");
        UUID student = seedStudent();
        studentEnrollment = seedEnrollment(student, courseA);
        UUID criterionId =
                seedActivityCriterion(classGroupA, 1, "Knowing", "Prueba 1", "Evaluacion escrita");
        eventId = seedEvent(criterionId, "Tema 1");
    }

    @Test
    void nonOwnerTeacher_setScore_forbidden_andNothingPersisted() throws Exception {
        String body = json.writeValueAsString(new SetScoreBody(studentEnrollment, eventId, "40"));

        mvc.perform(
                        post("/api/assessment-scores")
                                .header("Authorization", "Bearer " + tokenFor(teacherB, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());

        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM assessment_scores WHERE id_assessment_event = ?",
                        Integer.class,
                        eventId);
        org.assertj.core.api.Assertions.assertThat(count).isZero();
    }

    @Test
    void ownerTeacher_setScore_ok() throws Exception {
        String body = json.writeValueAsString(new SetScoreBody(studentEnrollment, eventId, "40"));

        mvc.perform(
                        post("/api/assessment-scores")
                                .header("Authorization", "Bearer " + tokenFor(teacherA, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void director_deletesScore_regardlessOfOwnership() throws Exception {
        jdbc.update(
                "INSERT INTO assessment_scores (id_assessment_score, id_course_enrollment, id_assessment_event, score) "
                        + "VALUES (?,?,?,?)",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                studentEnrollment,
                eventId,
                10.0);
        UUID scoreId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        mvc.perform(
                        delete("/api/assessment-scores/{id}", scoreId)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isNoContent());
    }

    @Test
    void ownerTeacher_byCriterion_returnsDirectScores() throws Exception {
        UUID direct = seedCriterion(classGroupA, 1, "Doing", "Participacion");
        seedCriterionScore(studentEnrollment, direct, 30);

        mvc.perform(
                        get("/api/assessment-scores/criterion/{criterionId}", direct)
                                .header("Authorization", "Bearer " + tokenFor(teacherA, "Teacher")))
                .andExpect(status().isOk());
    }

    @Test
    void nonOwnerTeacher_byCriterion_forbidden() throws Exception {
        UUID direct = seedCriterion(classGroupA, 1, "Doing", "Participacion");

        mvc.perform(
                        get("/api/assessment-scores/criterion/{criterionId}", direct)
                                .header("Authorization", "Bearer " + tokenFor(teacherB, "Teacher")))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonOwnerTeacher_byEvent_forbidden() throws Exception {
        mvc.perform(
                        get("/api/assessment-scores/event/{eventId}", eventId)
                                .header("Authorization", "Bearer " + tokenFor(teacherB, "Teacher")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerTeacherAndDirector_byEvent_ok() throws Exception {
        mvc.perform(
                        get("/api/assessment-scores/event/{eventId}", eventId)
                                .header("Authorization", "Bearer " + tokenFor(teacherA, "Teacher")))
                .andExpect(status().isOk());

        mvc.perform(
                        get("/api/assessment-scores/event/{eventId}", eventId)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isOk());
    }

    @Test
    void unknownEventId_setScore_deniesNotCrashes() throws Exception {
        String body =
                json.writeValueAsString(
                        new SetScoreBody(studentEnrollment, UUID.randomUUID(), "40"));

        mvc.perform(
                        post("/api/assessment-scores")
                                .header("Authorization", "Bearer " + tokenFor(teacherA, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isForbidden());
    }

    private record SetScoreBody(
            @com.fasterxml.jackson.annotation.JsonProperty("id_course_enrollment")
                    UUID courseEnrollmentId,
            @com.fasterxml.jackson.annotation.JsonProperty("id_assessment_event") UUID eventId,
            String score) {}
}
