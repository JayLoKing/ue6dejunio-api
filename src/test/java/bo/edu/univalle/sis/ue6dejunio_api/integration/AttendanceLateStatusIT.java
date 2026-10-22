package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * Characterization: {@code Late} is the fourth attendance status. The DB CHECK and every request
 * DTO already accept it, but nothing drove it through the HTTP endpoints until the web started
 * sending it. Pins that the daily batch (homeroom teacher) and the session batch (class-group
 * teacher) persist it, that it reads back unchanged, and that the course stats count it as late.
 */
class AttendanceLateStatusIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID homeroomTeacher;
    private UUID subjectTeacher;
    private UUID course;
    private UUID classGroup;
    private UUID enrollment;

    @BeforeEach
    void seed() {
        homeroomTeacher = seedUser("Teacher", false);
        subjectTeacher = seedUser("Teacher", false);
        course = seedCourse(homeroomTeacher, "A");
        classGroup = seedClassGroup(course, subjectTeacher, "Matematicas");
        enrollment = seedEnrollment(seedStudent(), course);
    }

    @Test
    void homeroomTeacher_savesLateInDailyBatch_andReadsItBack() throws Exception {
        LocalDate day = editableSchoolDay();
        String body =
                json.writeValueAsString(
                        new DailyBatchBody(day, List.of(new Mark(enrollment, "Late"))));

        mvc.perform(
                        post("/api/attendance/daily/batch")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        JsonNode rows = readEnrollmentAttendance();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("status").asText()).isEqualTo("Late");
        assertThat(rows.get(0).get("classGroupId").isNull()).isTrue();
        assertThat(rows.get(0).get("date").asText()).isEqualTo(day.toString());
    }

    @Test
    void dailyLate_isCountedAsLateInCourseStats() throws Exception {
        String body =
                json.writeValueAsString(
                        new DailyBatchBody(
                                editableSchoolDay(), List.of(new Mark(enrollment, "Late"))));

        mvc.perform(
                        post("/api/attendance/daily/batch")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        String stats =
                mvc.perform(
                                get("/api/courses/{id}/attendance-stats", course)
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(homeroomTeacher, "Teacher")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode overall = json.readTree(stats).get("overall");
        assertThat(overall.get("late").asLong()).isEqualTo(1);
        assertThat(overall.get("present").asLong()).isZero();
        assertThat(overall.get("absent").asLong()).isZero();
    }

    @Test
    void classGroupTeacher_savesLateInSessionBatch_andReadsItBack() throws Exception {
        String body =
                json.writeValueAsString(
                        new SessionBatchBody(
                                classGroup,
                                editableSchoolDay(),
                                List.of(new Mark(enrollment, "Late"))));

        mvc.perform(
                        post("/api/attendance/session/batch")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(subjectTeacher, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        JsonNode rows = readEnrollmentAttendance();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("status").asText()).isEqualTo("Late");
        assertThat(rows.get(0).get("classGroupId").asText()).isEqualTo(classGroup.toString());
    }

    private JsonNode readEnrollmentAttendance() throws Exception {
        String body =
                mvc.perform(
                                get("/api/attendance")
                                        .param("id_course_enrollment", enrollment.toString())
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(homeroomTeacher, "Teacher")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }

    /** See AttendanceAuthorizationIT: the one writable school day. */
    private static LocalDate editableSchoolDay() {
        LocalDate today = LocalDate.now(ZoneId.of("America/La_Paz"));
        return switch (today.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> today.with(DayOfWeek.FRIDAY);
            default -> today;
        };
    }

    private record DailyBatchBody(
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date, List<Mark> records) {}

    private record SessionBatchBody(
            @JsonProperty("id_class_group") UUID classGroupId,
            @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
            List<Mark> records) {}

    private record Mark(
            @JsonProperty("id_course_enrollment") UUID courseEnrollmentId, String status) {}
}
