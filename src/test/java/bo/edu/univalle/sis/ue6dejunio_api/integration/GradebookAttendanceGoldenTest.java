package bo.edu.univalle.sis.ue6dejunio_api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec: Output Equivalence (Course Attendance) — GET /api/gradebook/attendance.
 *
 * Same characterization/capture procedure as {@link GradebookCentralizerGoldenTest}; see that
 * class's javadoc. Requires Docker/Testcontainers — not executable in the implementation sandbox
 * for this change, see the apply-progress report.
 */
class GradebookAttendanceGoldenTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private static final Path GOLDEN_PATH =
        Path.of("src/test/resources/gradebook/golden/attendance-page1.json");

    private UUID director;
    private UUID courseId;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        UUID teacher = seedUser("Teacher", false);
        courseId = seedCourse(teacher, "A");

        // Student with two daily attendance records.
        UUID studentWithAttendance = seedStudent();
        UUID enrollmentWithAttendance = seedEnrollment(studentWithAttendance, courseId);
        seedDailyAttendance(enrollmentWithAttendance, LocalDate.of(2026, 3, 2), "Present");
        seedDailyAttendance(enrollmentWithAttendance, LocalDate.of(2026, 3, 3), "Absent");

        // Student enrolled but with zero attendance records (empty-attendance scenario).
        UUID studentNoAttendance = seedStudent();
        seedEnrollment(studentNoAttendance, courseId);
    }

    private void seedDailyAttendance(UUID enrollmentId, LocalDate date, String status) {
        jdbc.update(
            "INSERT INTO attendance (id_attendance, id_course_enrollment, id_class_group, date, status) "
                + "VALUES (?,?,NULL,?,?)",
            UUID.randomUUID(), enrollmentId, date, status);
    }

    @Test
    void attendancePage1_matchesCommittedGolden() throws Exception {
        String actual = mvc.perform(get("/api/gradebook/attendance")
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("id_course", courseId.toString())
                .param("offset", "1")
                .param("limit", "30"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        GradebookCentralizerGoldenTest.assertGoldenMatch(GOLDEN_PATH, actual);
    }
}
