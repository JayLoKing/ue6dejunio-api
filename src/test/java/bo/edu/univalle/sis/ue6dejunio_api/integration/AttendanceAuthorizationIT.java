package bo.edu.univalle.sis.ue6dejunio_api.integration;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec: Daily attendance homeroom ownership guard, dailyBatch atomicity, attendance read guard,
 * and session() regression (unchanged @authz.canWriteClassGroup guard).
 */
class AttendanceAuthorizationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private UUID homeroomTeacherA;
    private UUID otherTeacher;
    private UUID director;
    private UUID courseA;
    private UUID courseB;
    private UUID enrollmentInA;
    private UUID enrollmentInB;
    private UUID classGroupA;

    @BeforeEach
    void seed() {
        homeroomTeacherA = seedUser("Teacher", false);
        otherTeacher = seedUser("Teacher", false);
        director = seedUser("Director", false);
        courseA = seedCourse(homeroomTeacherA, "A");
        courseB = seedCourse(otherTeacher, "B");
        classGroupA = seedClassGroup(courseA, homeroomTeacherA, "Matematicas");
        UUID studentA = seedStudent();
        UUID studentB = seedStudent();
        enrollmentInA = seedEnrollment(studentA, courseA);
        enrollmentInB = seedEnrollment(studentB, courseB);
    }

    @Test
    void nonHomeroomTeacher_daily_forbidden() throws Exception {
        String body = json.writeValueAsString(new DailyBody(enrollmentInA, LocalDate.now(), "Present"));

        mvc.perform(post("/api/attendance/daily")
                .header("Authorization", "Bearer " + tokenFor(otherTeacher, "Teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void homeroomTeacher_daily_ok() throws Exception {
        String body = json.writeValueAsString(new DailyBody(enrollmentInA, LocalDate.now(), "Present"));

        mvc.perform(post("/api/attendance/daily")
                .header("Authorization", "Bearer " + tokenFor(homeroomTeacherA, "Teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    @Test
    void mixedOwnershipBatch_forbidden_nothingPersisted() throws Exception {
        String body = json.writeValueAsString(new DailyBatchBody(LocalDate.now(),
            List.of(new Mark(enrollmentInA, "Present"), new Mark(enrollmentInB, "Present"))));

        mvc.perform(post("/api/attendance/daily/batch")
                .header("Authorization", "Bearer " + tokenFor(homeroomTeacherA, "Teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM attendance WHERE id_course_enrollment IN (?, ?)", Integer.class,
            enrollmentInA, enrollmentInB);
        assertThat(count).isZero();
    }

    @Test
    void director_dailyAndBatch_ok() throws Exception {
        String dailyBody = json.writeValueAsString(new DailyBody(enrollmentInB, LocalDate.now(), "Present"));
        mvc.perform(post("/api/attendance/daily")
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(dailyBody))
            .andExpect(status().isOk());

        String batchBody = json.writeValueAsString(new DailyBatchBody(LocalDate.now().minusDays(1),
            List.of(new Mark(enrollmentInA, "Present"), new Mark(enrollmentInB, "Absent"))));
        mvc.perform(post("/api/attendance/daily/batch")
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchBody))
            .andExpect(status().isOk());
    }

    @Test
    void nonOwnerTeacher_byCourseEnrollment_read_forbidden() throws Exception {
        mvc.perform(get("/api/attendance").param("id_course_enrollment", enrollmentInA.toString())
                .header("Authorization", "Bearer " + tokenFor(otherTeacher, "Teacher")))
            .andExpect(status().isForbidden());
    }

    @Test
    void session_regression_nonOwnerForbidden_ownerAndDirectorOk() throws Exception {
        String body = json.writeValueAsString(
            new SessionBody(enrollmentInA, classGroupA, LocalDate.now(), "Present"));

        mvc.perform(post("/api/attendance/session")
                .header("Authorization", "Bearer " + tokenFor(otherTeacher, "Teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/attendance/session")
                .header("Authorization", "Bearer " + tokenFor(homeroomTeacherA, "Teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());
    }

    private record DailyBody(
        @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        String status
    ) {}

    private record Mark(
        @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
        String status
    ) {}

    private record DailyBatchBody(
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        List<Mark> records
    ) {}

    private record SessionBody(
        @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
        @JsonProperty("id_class_group") UUID classGroupId,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        String status
    ) {}
}
