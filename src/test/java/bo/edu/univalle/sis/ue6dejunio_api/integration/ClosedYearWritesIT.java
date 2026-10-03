package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A closed gestión rejects every academic write with a 409 and keeps serving reads; reopening lets
 * writes through again. The owning teacher drives every notebook write (the Director cannot write
 * it), so the refusal is a 409 from the use case and not a 403 from authz; the Director opens and
 * closes the year and reads.
 */
class ClosedYearWritesIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private UUID course;
    private UUID classGroup;
    private UUID enrollment;
    private UUID criterion;
    private UUID activityCriterion;
    private UUID event;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", true);
        course = seedCourse(teacher, "A");
        classGroup = seedClassGroup(course, teacher, "Matematicas");
        enrollment = seedEnrollment(seedStudent(), course);
        criterion = seedCriterion(classGroup, 1, "Doing", "Participacion");
        activityCriterion =
                seedActivityCriterion(classGroup, 1, "Knowing", "Cuadernos", "Revision");
        event = seedEvent(activityCriterion, "Tema 1");
    }

    private void closeYear() {
        jdbc.update(
                "UPDATE academic_years SET closed_at = now(), active = false WHERE year = 2026");
    }

    /** The teacher who owns the course and its class group: the notebook's only writer. */
    private String bearer() {
        return "Bearer " + tokenFor(teacher, "Teacher");
    }

    private String directorBearer() {
        return "Bearer " + tokenFor(director, "Director");
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mvc.perform(
                post(url)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private ResultActions putJson(String url, Object body) throws Exception {
        return mvc.perform(
                put(url).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private ResultActions score(UUID target, boolean isEvent, String value) throws Exception {
        return postJson(
                "/api/assessment-scores",
                new ScoreBody(
                        enrollment,
                        isEvent ? target : null,
                        isEvent ? null : target,
                        new BigDecimal(value)));
    }

    private ResultActions createCriterion() throws Exception {
        return postJson("/api/criteria", new CriterionBody(classGroup, 1, "Being", "Puntualidad"));
    }

    // ---- scores ----

    @Test
    void closedYear_rejectsAScoreOnACriterion() throws Exception {
        closeYear();

        score(criterion, false, "30")
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value("La gestión 2026 está cerrada; no admite cambios."));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_scores", Integer.class))
                .isZero();
    }

    @Test
    void closedYear_rejectsAScoreOnAnActivityItem() throws Exception {
        closeYear();

        score(event, true, "30").andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_scores", Integer.class))
                .isZero();
    }

    @Test
    void closedYear_rejectsDeletingAScore_andKeepsTheRow() throws Exception {
        seedCriterionScore(enrollment, criterion, 30);
        UUID scoreId =
                jdbc.queryForObject(
                        "SELECT id_assessment_score FROM assessment_scores", UUID.class);
        closeYear();

        mvc.perform(delete("/api/assessment-scores/" + scoreId).header("Authorization", bearer()))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_scores", Integer.class))
                .isEqualTo(1);
    }

    // ---- criteria and events ----

    @Test
    void closedYear_rejectsCreatingACriterion() throws Exception {
        closeYear();

        createCriterion().andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_criteria", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void closedYear_rejectsRenamingAndDeletingACriterion() throws Exception {
        closeYear();

        putJson("/api/criteria/" + criterion, new NameBody("Otro"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/criteria/" + criterion).header("Authorization", bearer()))
                .andExpect(status().isConflict());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM evaluation_criteria WHERE id_criterion = ?",
                                String.class,
                                criterion))
                .isEqualTo("Participacion");
    }

    @Test
    void closedYear_rejectsCreatingAnEvent() throws Exception {
        closeYear();

        postJson("/api/assessment-events", new EventBody(activityCriterion, "Tema 2"))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_events", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void closedYear_rejectsUpdatingAndDeletingAnEvent() throws Exception {
        closeYear();

        putJson("/api/assessment-events/" + event, new TitleBody("Otro"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/assessment-events/" + event).header("Authorization", bearer()))
                .andExpect(status().isConflict());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT title FROM assessment_events WHERE id_assessment_event = ?",
                                String.class,
                                event))
                .isEqualTo("Tema 1");
    }

    // ---- reads stay open, reopening lets writes through ----

    @Test
    void closedYear_stillServesReads() throws Exception {
        seedCriterionScore(enrollment, criterion, 30);
        closeYear();

        mvc.perform(
                        get("/api/assessment-scores/criterion/" + criterion)
                                .header("Authorization", directorBearer()))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/gradebook/report-card")
                                .param("id_course_enrollment", enrollment.toString())
                                .header("Authorization", directorBearer()))
                .andExpect(status().isOk());
    }

    @Test
    void reopeningTheYear_lets_writesThroughAgain() throws Exception {
        closeYear();
        score(criterion, false, "30").andExpect(status().isConflict());

        mvc.perform(
                        put("/api/academic-years/" + academicYearId(2026) + "/reopen")
                                .header("Authorization", directorBearer()))
                .andExpect(status().isOk());

        score(criterion, false, "30").andExpect(status().isOk());
        createCriterion().andExpect(status().is2xxSuccessful());
    }

    @Test
    void closingThroughTheEndpoint_alsoStopsWrites() throws Exception {
        mvc.perform(
                        put("/api/academic-years/" + academicYearId(2026) + "/close")
                                .header("Authorization", directorBearer()))
                .andExpect(status().isOk());

        score(criterion, false, "30").andExpect(status().isConflict());
    }

    @Test
    void anotherYearStaysWritableWhileThisOneIsClosed() throws Exception {
        UUID course2025 = seedCourse(teacher, "B", academicYearId(2025));
        UUID group2025 = seedClassGroup(course2025, teacher, "Matematicas");
        closeYear();

        postJson("/api/criteria", new CriterionBody(group2025, 1, "Being", "Puntualidad"))
                .andExpect(status().is2xxSuccessful());
    }

    private record ScoreBody(
            @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
            @JsonProperty("id_assessment_event") UUID eventId,
            @JsonProperty("id_criterion") UUID criterionId,
            BigDecimal score) {}

    private record CriterionBody(
            @JsonProperty("id_class_group") UUID classGroupId,
            int trimester,
            String dimension,
            String name) {}

    private record NameBody(String name) {}

    private record EventBody(@JsonProperty("id_criterion") UUID criterionId, String title) {}

    private record TitleBody(String title) {}

    // ---- attendance ----

    @Test
    void closedYear_rejectsDailyAttendance_singleAndBatch() throws Exception {
        closeYear();

        postJson("/api/attendance/daily", new DailyBody(enrollment, editableSchoolDay(), "Present"))
                .andExpect(status().isConflict());
        postJson(
                        "/api/attendance/daily/batch",
                        new DailyBatchBody(
                                editableSchoolDay(), List.of(new Mark(enrollment, "Present"))))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance", Integer.class)).isZero();
    }

    @Test
    void closedYear_rejectsSessionAttendance_singleAndBatch() throws Exception {
        closeYear();

        postJson(
                        "/api/attendance/session",
                        new SessionBody(enrollment, classGroup, editableSchoolDay(), "Present"))
                .andExpect(status().isConflict());
        postJson(
                        "/api/attendance/session/batch",
                        new SessionBatchBody(
                                classGroup,
                                editableSchoolDay(),
                                List.of(new Mark(enrollment, "Present"))))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance", Integer.class)).isZero();
    }

    @Test
    void closedYear_stillServesAttendanceReads() throws Exception {
        closeYear();

        mvc.perform(
                        get("/api/attendance")
                                .param("id_course_enrollment", enrollment.toString())
                                .header("Authorization", directorBearer()))
                .andExpect(status().isOk());
    }

    @Test
    void reopeningTheYear_letsAttendanceThrough() throws Exception {
        closeYear();
        postJson("/api/attendance/daily", new DailyBody(enrollment, editableSchoolDay(), "Present"))
                .andExpect(status().isConflict());

        mvc.perform(
                        put("/api/academic-years/" + academicYearId(2026) + "/reopen")
                                .header("Authorization", directorBearer()))
                .andExpect(status().isOk());

        postJson("/api/attendance/daily", new DailyBody(enrollment, editableSchoolDay(), "Present"))
                .andExpect(status().is2xxSuccessful());
    }

    /** Keeps the date inside the editable Mon-Fri window; see AttendanceAuthorizationIT. */
    private static LocalDate editableSchoolDay() {
        LocalDate today = LocalDate.now(ZoneId.of("America/La_Paz"));
        return switch (today.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> today.with(DayOfWeek.FRIDAY);
            default -> today;
        };
    }

    private record DailyBody(
            @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
            String status) {}

    private record Mark(
            @JsonProperty("id_course_enrollment") UUID courseEnrollmentId, String status) {}

    private record DailyBatchBody(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date, List<Mark> records) {}

    private record SessionBody(
            @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
            @JsonProperty("id_class_group") UUID classGroupId,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
            String status) {}

    private record SessionBatchBody(
            @JsonProperty("id_class_group") UUID classGroupId,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
            List<Mark> records) {}
}
