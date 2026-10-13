package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The pedagogical notebook (criteria, activities, scores, attendance) is written only by the
 * teacher who owns it; the Director reads it. Every notebook write endpoint answers the Director
 * with 403 and writes nothing, while the owning teacher still succeeds and the Director still reads
 * the same data. Non-notebook class-group work (running the risk model on a subject) keeps the
 * Director.
 */
class NotebookWriteDirectorForbiddenIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID teacher;
    private UUID director;
    private UUID courseId;
    private UUID classGroupId;
    private UUID enrollmentId;
    private UUID directCriterionId;
    private UUID activityCriterionId;
    private UUID eventId;

    @BeforeEach
    void seed() {
        teacher = seedUser("Teacher", false);
        director = seedUser("Director", false);
        courseId = seedCourse(teacher, "A");
        classGroupId = seedClassGroup(courseId, teacher, "Matematicas");
        enrollmentId = seedEnrollment(seedStudent(), courseId);
        directCriterionId = seedCriterion(classGroupId, 1, "Doing", "Participacion");
        activityCriterionId =
                seedActivityCriterion(classGroupId, 1, "Knowing", "Prueba 1", "Evaluacion");
        eventId = seedEvent(activityCriterionId, "Tema 1");
    }

    // ---- criteria ----

    @Test
    void createCriterion_director403_nothingWritten_teacherOk() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "id_class_group",
                        classGroupId,
                        "trimester",
                        1,
                        "dimension",
                        "Being",
                        "name",
                        "Respeto");

        send(post("/api/criteria"), body, director).andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM evaluation_criteria WHERE name = 'Respeto'"))
                .isZero();

        send(post("/api/criteria"), body, teacher).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM evaluation_criteria WHERE name = 'Respeto'"))
                .isEqualTo(1);
    }

    @Test
    void updateCriterion_director403_nameUnchanged_teacherOk() throws Exception {
        Map<String, Object> body = Map.of("name", "Renombrado");

        send(put("/api/criteria/{id}", directCriterionId), body, director)
                .andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM evaluation_criteria WHERE name = 'Renombrado'"))
                .isZero();

        send(put("/api/criteria/{id}", directCriterionId), body, teacher)
                .andExpect(status().isOk());
    }

    @Test
    void deleteCriterion_director403_rowKept_teacherOk() throws Exception {
        send(delete("/api/criteria/{id}", directCriterionId), null, director)
                .andExpect(status().isForbidden());
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM evaluation_criteria WHERE id_criterion = '"
                                        + directCriterionId
                                        + "'"))
                .isEqualTo(1);

        send(delete("/api/criteria/{id}", directCriterionId), null, teacher)
                .andExpect(status().isNoContent());
    }

    // ---- activity criteria (assessment events) ----

    @Test
    void createEvent_director403_nothingWritten_teacherOk() throws Exception {
        Map<String, Object> body = Map.of("id_criterion", activityCriterionId, "title", "Tema 2");

        send(post("/api/assessment-events"), body, director).andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM assessment_events WHERE title = 'Tema 2'")).isZero();

        send(post("/api/assessment-events"), body, teacher).andExpect(status().isOk());
    }

    @Test
    void updateEvent_director403_titleUnchanged_teacherOk() throws Exception {
        Map<String, Object> body = Map.of("title", "Otro");

        send(put("/api/assessment-events/{id}", eventId), body, director)
                .andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM assessment_events WHERE title = 'Otro'")).isZero();

        send(put("/api/assessment-events/{id}", eventId), body, teacher).andExpect(status().isOk());
    }

    @Test
    void deleteEvent_director403_rowKept_teacherOk() throws Exception {
        // An activity keeps at least one item, so the teacher's delete needs a second one to
        // remove.
        seedEvent(activityCriterionId, "Tema 2");

        send(delete("/api/assessment-events/{id}", eventId), null, director)
                .andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM assessment_events")).isEqualTo(2);

        send(delete("/api/assessment-events/{id}", eventId), null, teacher)
                .andExpect(status().isNoContent());
    }

    // ---- scores ----

    @Test
    void setScoreOnEvent_director403_nothingWritten_teacherOk() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "id_course_enrollment", enrollmentId,
                        "id_assessment_event", eventId,
                        "score", "40");

        send(post("/api/assessment-scores"), body, director).andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM assessment_scores")).isZero();

        send(post("/api/assessment-scores"), body, teacher).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM assessment_scores")).isEqualTo(1);
    }

    @Test
    void setScoreOnCriterion_director403_nothingWritten_teacherOk() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "id_course_enrollment", enrollmentId,
                        "id_criterion", directCriterionId,
                        "score", "30");

        send(post("/api/assessment-scores"), body, director).andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM assessment_scores")).isZero();

        send(post("/api/assessment-scores"), body, teacher).andExpect(status().isOk());
    }

    @Test
    void deleteScore_director403_rowKept_teacherOk() throws Exception {
        seedEventScore(enrollmentId, eventId, 10.0);
        UUID scoreId =
                jdbc.queryForObject(
                        "SELECT id_assessment_score FROM assessment_scores", UUID.class);

        send(delete("/api/assessment-scores/{id}", scoreId), null, director)
                .andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM assessment_scores")).isEqualTo(1);

        send(delete("/api/assessment-scores/{id}", scoreId), null, teacher)
                .andExpect(status().isNoContent());
    }

    // ---- attendance ----

    @Test
    void dailyAttendance_director403_nothingWritten_homeroomTeacherOk() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "id_course_enrollment",
                        enrollmentId,
                        "date",
                        schoolDay().toString(),
                        "status",
                        "Present");

        send(post("/api/attendance/daily"), body, director).andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM attendance")).isZero();

        send(post("/api/attendance/daily"), body, teacher).andExpect(status().isOk());
    }

    @Test
    void dailyBatch_director403_nothingWritten_homeroomTeacherOk() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "date",
                        schoolDay().toString(),
                        "records",
                        List.of(Map.of("id_course_enrollment", enrollmentId, "status", "Present")));

        send(post("/api/attendance/daily/batch"), body, director).andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM attendance")).isZero();

        send(post("/api/attendance/daily/batch"), body, teacher).andExpect(status().isOk());
    }

    @Test
    void sessionAttendance_director403_nothingWritten_ownerOk() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "id_course_enrollment",
                        enrollmentId,
                        "id_class_group",
                        classGroupId,
                        "date",
                        schoolDay().toString(),
                        "status",
                        "Present");

        send(post("/api/attendance/session"), body, director).andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM attendance")).isZero();

        send(post("/api/attendance/session"), body, teacher).andExpect(status().isOk());
    }

    @Test
    void sessionBatch_director403_nothingWritten_ownerOk() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "id_class_group",
                        classGroupId,
                        "date",
                        schoolDay().toString(),
                        "records",
                        List.of(Map.of("id_course_enrollment", enrollmentId, "status", "Present")));

        send(post("/api/attendance/session/batch"), body, director)
                .andExpect(status().isForbidden());
        assertThat(count("SELECT COUNT(*) FROM attendance")).isZero();

        send(post("/api/attendance/session/batch"), body, teacher).andExpect(status().isOk());
    }

    // ---- the Director still reads ----

    @Test
    void director_stillReadsTheNotebook() throws Exception {
        seedEventScore(enrollmentId, eventId, 10.0);

        send(get("/api/criteria?id_class_group={cg}&trimester=1", classGroupId), null, director)
                .andExpect(status().isOk());
        send(get("/api/assessment-events?id_criterion={c}", activityCriterionId), null, director)
                .andExpect(status().isOk());
        send(get("/api/assessment-scores/event/{id}", eventId), null, director)
                .andExpect(status().isOk());
        send(get("/api/assessment-scores/criterion/{id}", directCriterionId), null, director)
                .andExpect(status().isOk());
        send(get("/api/assessment-scores?id_course_enrollment={ce}", enrollmentId), null, director)
                .andExpect(status().isOk());
        send(get("/api/attendance?id_course_enrollment={ce}", enrollmentId), null, director)
                .andExpect(status().isOk());
    }

    // ---- what the Director keeps ----

    @Test
    void director_stillRunsTheRiskModelOnASubject() throws Exception {
        // Not a notebook write: it guards on canWriteClassGroup, which keeps the Director. The
        // model service is not up under test, so any outcome but a refusal proves the guard passed.
        int statusCode =
                send(
                                post(
                                        "/api/class-groups/{id}/risk/predict?trimester=1",
                                        classGroupId),
                                null,
                                director)
                        .andReturn()
                        .getResponse()
                        .getStatus();

        assertThat(statusCode).isNotEqualTo(403);
    }

    private org.springframework.test.web.servlet.ResultActions send(
            MockHttpServletRequestBuilder request, Object body, UUID user) throws Exception {
        String role = user.equals(director) ? "Director" : "Teacher";
        request.header("Authorization", "Bearer " + tokenFor(user, role));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    /** The one writable school day; authorization, not the date window, is under test. */
    private static LocalDate schoolDay() {
        LocalDate today = LocalDate.now(ZoneId.of("America/La_Paz"));
        return switch (today.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> today.with(DayOfWeek.FRIDAY);
            default -> today;
        };
    }
}
