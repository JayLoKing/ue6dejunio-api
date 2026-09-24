package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec: attendance audit trail. Every record keeps who first marked it and who last changed it.
 * Because both write paths are upserts, a correction must move updated_by/updated_at while leaving
 * created_by/created_at on the original author.
 */
class AttendanceAuditTrailIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID homeroomTeacher;
    private UUID director;
    private UUID enrollment;

    @BeforeEach
    void seed() {
        homeroomTeacher = seedUser("Teacher", false);
        director = seedUser("Director", false);
        UUID course = seedCourse(homeroomTeacher, "A");
        enrollment = seedEnrollment(seedStudent(), course);
    }

    @Test
    void dailyAttendance_recordsAuthorAndTimestamp() throws Exception {
        postDaily(homeroomTeacher, "Teacher", "Present");

        Map<String, Object> row = auditRow();
        assertThat(row.get("created_by")).isEqualTo(homeroomTeacher);
        assertThat(row.get("updated_by")).isEqualTo(homeroomTeacher);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    void correction_movesUpdatedAuthor_andKeepsOriginalAuthor() throws Exception {
        postDaily(homeroomTeacher, "Teacher", "Present");
        Map<String, Object> afterCreate = auditRow();

        // Director corrects the same (enrollment, date) row: the unique index makes this an update,
        // not a second row.
        postDaily(director, "Director", "Absent");

        Map<String, Object> afterEdit = auditRow();
        assertThat(afterEdit.get("status")).isEqualTo("Absent");
        assertThat(afterEdit.get("created_by"))
                .as("original author must survive the correction")
                .isEqualTo(homeroomTeacher);
        assertThat(afterEdit.get("updated_by")).isEqualTo(director);
        assertThat(afterEdit.get("created_at")).isEqualTo(afterCreate.get("created_at"));
        assertThat((Timestamp) afterEdit.get("updated_at"))
                .isAfterOrEqualTo((Timestamp) afterCreate.get("updated_at"));

        Integer rows =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM attendance WHERE id_course_enrollment = ?",
                        Integer.class,
                        enrollment);
        assertThat(rows).isEqualTo(1);
    }

    private void postDaily(UUID userId, String role, String status) throws Exception {
        String body =
                json.writeValueAsString(new DailyBody(enrollment, editableSchoolDay(), status));
        mvc.perform(
                        post("/api/attendance/daily")
                                .header("Authorization", "Bearer " + tokenFor(userId, role))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());
    }

    private Map<String, Object> auditRow() {
        return jdbc.queryForMap(
                "SELECT status, created_by, updated_by, created_at, updated_at FROM attendance "
                        + "WHERE id_course_enrollment = ? AND id_class_group IS NULL",
                enrollment);
    }

    /** See AttendanceAuthorizationIT: keeps the date inside the editable Mon-Fri window. */
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
}
