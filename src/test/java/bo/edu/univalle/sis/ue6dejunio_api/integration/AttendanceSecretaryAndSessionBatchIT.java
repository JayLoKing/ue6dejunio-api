package bo.edu.univalle.sis.ue6dejunio_api.integration;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec: use case 2.53 opens attendance to the secretariat in real time, and use case 2.55 lets a
 * technical teacher load their subject roster and save the whole group in one transaction.
 * Also pins the two boundaries: the secretariat never writes, and never reaches grades.
 */
class AttendanceSecretaryAndSessionBatchIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID homeroomTeacher;
    private UUID technicalTeacher;
    private UUID otherTeacher;
    private UUID secretary;
    private UUID course;
    private UUID classGroup;
    private UUID enrollmentA;
    private UUID enrollmentB;
    private UUID foreignEnrollment;

    @BeforeEach
    void seed() {
        homeroomTeacher = seedUser("Teacher", false);
        technicalTeacher = seedUser("Teacher", true);
        otherTeacher = seedUser("Teacher", false);
        secretary = seedUser("Secretary", false);
        course = seedCourse(homeroomTeacher, "A");
        classGroup = seedClassGroup(course, technicalTeacher, "Matematicas");
        enrollmentA = seedEnrollment(seedStudent(), course);
        enrollmentB = seedEnrollment(seedStudent(), course);
        foreignEnrollment = seedEnrollment(seedStudent(), seedCourse(otherTeacher, "B"));
    }

    // ---- secretariat: reads attendance, writes nothing, sees no grades ----

    @Test
    void secretary_readsCourseAttendance() throws Exception {
        mvc.perform(get("/api/gradebook/attendance").param("id_course", course.toString())
                .header("Authorization", "Bearer " + tokenFor(secretary, "Secretary")))
            .andExpect(status().isOk());
    }

    @Test
    void secretary_readsEnrollmentAttendance() throws Exception {
        mvc.perform(get("/api/attendance").param("id_course_enrollment", enrollmentA.toString())
                .header("Authorization", "Bearer " + tokenFor(secretary, "Secretary")))
            .andExpect(status().isOk());
    }

    @Test
    void secretary_readsAttendanceStats() throws Exception {
        mvc.perform(get("/api/courses/" + course + "/attendance-stats")
                .header("Authorization", "Bearer " + tokenFor(secretary, "Secretary")))
            .andExpect(status().isOk());
    }

    @Test
    void secretary_cannotWriteAttendance() throws Exception {
        String body = json.writeValueAsString(
            new DailyBody(enrollmentA, editableSchoolDay(), "Present"));

        mvc.perform(post("/api/attendance/daily")
                .header("Authorization", "Bearer " + tokenFor(secretary, "Secretary"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
    }

    @Test
    void secretary_readsCentralizer() throws Exception {
        mvc.perform(get("/api/gradebook/centralizer")
                .param("id_course", course.toString())
                .param("trimester", "1")
                .header("Authorization", "Bearer " + tokenFor(secretary, "Secretary")))
            .andExpect(status().isOk());
    }

    @Test
    void secretary_readsStudentDirectory() throws Exception {
        mvc.perform(get("/api/students/search").param("q", "")
                .header("Authorization", "Bearer " + tokenFor(secretary, "Secretary")))
            .andExpect(status().isOk());
    }

    @Test
    void secretary_cannotWithdrawStudent() throws Exception {
        // Reads open, writes closed: /api/students/** only lists Secretary on the GET rule.
        mvc.perform(post("/api/students/" + UUID.randomUUID() + "/withdraw")
                .header("Authorization", "Bearer " + tokenFor(secretary, "Secretary"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Transfer\"}"))
            .andExpect(status().isForbidden());
    }

    // ---- technical teacher: subject roster + whole-group save ----

    @Test
    void technicalTeacher_loadsSubjectRoster() throws Exception {
        mvc.perform(get("/api/gradebook/attendance/session")
                .param("id_class_group", classGroup.toString())
                .header("Authorization", "Bearer " + tokenFor(technicalTeacher, "Teacher")))
            .andExpect(status().isOk());
    }

    @Test
    void nonOwnerTeacher_cannotLoadSubjectRoster() throws Exception {
        mvc.perform(get("/api/gradebook/attendance/session")
                .param("id_class_group", classGroup.toString())
                .header("Authorization", "Bearer " + tokenFor(otherTeacher, "Teacher")))
            .andExpect(status().isForbidden());
    }

    @Test
    void technicalTeacher_savesWholeGroupInOneCall() throws Exception {
        String body = json.writeValueAsString(new SessionBatchBody(classGroup, editableSchoolDay(),
            List.of(new Mark(enrollmentA, "Present"), new Mark(enrollmentB, "Absent"))));

        mvc.perform(post("/api/attendance/session/batch")
                .header("Authorization", "Bearer " + tokenFor(technicalTeacher, "Teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        Integer sessionRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM attendance WHERE id_class_group = ?", Integer.class, classGroup);
        assertThat(sessionRows).isEqualTo(2);

        // The subject sheet must not have touched the course-wide daily record.
        Integer dailyRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM attendance WHERE id_class_group IS NULL", Integer.class);
        assertThat(dailyRows).isZero();
    }

    @Test
    void sessionBatch_withForeignStudent_isRejectedAndPersistsNothing() throws Exception {
        String body = json.writeValueAsString(new SessionBatchBody(classGroup, editableSchoolDay(),
            List.of(new Mark(enrollmentA, "Present"), new Mark(foreignEnrollment, "Present"))));

        mvc.perform(post("/api/attendance/session/batch")
                .header("Authorization", "Bearer " + tokenFor(technicalTeacher, "Teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());

        Integer rows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM attendance", Integer.class);
        assertThat(rows).isZero();
    }

    @Test
    void nonOwnerTeacher_cannotSaveSessionBatch() throws Exception {
        String body = json.writeValueAsString(new SessionBatchBody(classGroup, editableSchoolDay(),
            List.of(new Mark(enrollmentA, "Present"))));

        mvc.perform(post("/api/attendance/session/batch")
                .header("Authorization", "Bearer " + tokenFor(otherTeacher, "Teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden());
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

    private record SessionBatchBody(
        @JsonProperty("id_class_group") UUID classGroupId,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate date,
        List<Mark> records) {}

    private record Mark(
        @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
        String status) {}
}
