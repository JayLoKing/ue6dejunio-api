package bo.edu.univalle.sis.ue6dejunio_api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec: RF32 - POST /api/students/{id}/withdraw. Flips students.status to Withdrawn with the
 * given reason, cascades to the student's active course enrollment, drops from the default
 * active search, and keeps history readable. Repeat call conflicts (409); invalid reason (400);
 * unknown student (404).
 */
class StudentWithdrawIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private UUID course;
    private UUID student;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        course = seedCourse(teacher, "A");
        student = seedStudent();
        seedEnrollment(student, course);
    }

    @Test
    void withdraw_activeStudent_returns204AndFlipsStatusAndEnrollment() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Retiro Voluntario\"}"))
            .andExpect(status().isNoContent());

        String status = jdbc.queryForObject(
            "SELECT status FROM students WHERE id_student = ?", String.class, student);
        String reason = jdbc.queryForObject(
            "SELECT status_reason FROM students WHERE id_student = ?", String.class, student);
        String enrollmentStatus = jdbc.queryForObject(
            "SELECT status FROM course_enrollments WHERE id_student = ?", String.class, student);

        assertThat(status).isEqualTo("Withdrawn");
        assertThat(reason).isEqualTo("Retiro Voluntario");
        assertThat(enrollmentStatus).isEqualTo("Withdrawn");
    }

    @Test
    void withdraw_droppedFromDefaultActiveSearch() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Otro\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/students/search")
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.id=='" + student + "')]").isEmpty());
    }

    @Test
    void withdraw_historyRemainsReadableById() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Transferencia\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/students/{id}", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(student.toString()));
    }

    @Test
    void withdraw_alreadyWithdrawn_returns409() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Otro\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Otro\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void withdraw_nonexistentStudent_returns404() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", UUID.randomUUID())
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Otro\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void withdraw_invalidReason_returns400() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"No existe\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void withdraw_teacherAllowed() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                .contentType("application/json")
                .content("{\"reason\":\"Otro\"}"))
            .andExpect(status().isNoContent());
    }
}
