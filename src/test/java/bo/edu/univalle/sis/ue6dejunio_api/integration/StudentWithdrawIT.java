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
                .content("{\"reason\":\"Otro\",\"note\":\"Se mudo.\"}"))
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
                .content("{\"reason\":\"Otro\",\"note\":\"Se mudo.\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Otro\",\"note\":\"Se mudo.\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void withdraw_nonexistentStudent_returns404() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", UUID.randomUUID())
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Otro\",\"note\":\"Se mudo.\"}"))
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

    /**
     * A teacher registers and corrects the students of their own course, and may not take one off
     * the roll: a withdrawal ends every enrolment and drops the student from every listing in the
     * school. This was open to Teacher and is now the Director's alone.
     */
    @Test
    void withdraw_teacherRefused() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                .contentType("application/json")
                .content("{\"reason\":\"Otro\",\"note\":\"Se mudó.\"}"))
            .andExpect(status().isForbidden());

        String status = jdbc.queryForObject(
            "SELECT status FROM students WHERE id_student = ?", String.class, student);
        assertThat(status).isEqualTo("Effective");
    }

    /** "Otro" alone puts the word "Otro" in front of a teacher and answers nothing. */
    @Test
    void withdraw_theOpenReasonWithoutWords_returns400() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Otro\"}"))
            .andExpect(status().isBadRequest());
    }

    /** Who decided and when, written down: the notice is useless without somebody to ask. */
    @Test
    void withdraw_recordsTheWordsTheAuthorAndTheMoment() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Otro\",\"note\":\"Se mudó a Santa Cruz.\"}"))
            .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
            "SELECT status_reason FROM students WHERE id_student = ?", String.class, student))
            .isEqualTo("Otro");
        assertThat(jdbc.queryForObject(
            "SELECT status_note FROM students WHERE id_student = ?", String.class, student))
            .isEqualTo("Se mudó a Santa Cruz.");
        assertThat(jdbc.queryForObject(
            "SELECT status_changed_by FROM students WHERE id_student = ?", UUID.class, student))
            .isEqualTo(director);
        assertThat(jdbc.queryForObject(
            "SELECT status_changed_at FROM students WHERE id_student = ?", Object.class, student))
            .isNotNull();
    }

    /**
     * The whole chain: the Director withdraws, and the teacher whose roster just shrank is told
     * without having to open it.
     *
     * <p>An integration test because what could break here is the query that finds those teachers,
     * and the listener running after the withdrawal commits. Neither is visible to a mock.
     */
    @Test
    void withdraw_tellsTheTeacherWhoseCourseTheStudentWasIn() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Transferencia\",\"note\":\"A la U.E. San Martín.\"}"))
            .andExpect(status().isNoContent());

        String message = jdbc.queryForObject(
            "SELECT message FROM notifications WHERE receiver_id = ? AND type = ?",
            String.class, teacher, "STUDENT_WITHDRAWN");

        assertThat(message).contains("Transferencia").contains("A la U.E. San Martín.");
        // Nobody signed it: the withdrawal wrote it, not a person.
        assertThat(jdbc.queryForObject(
            "SELECT sender_id FROM notifications WHERE receiver_id = ? AND type = ?",
            UUID.class, teacher, "STUDENT_WITHDRAWN")).isNull();
    }

    /** A refused withdrawal announces nothing: the student is still on the roll. */
    @Test
    void withdraw_refused_tellsNobody() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                .contentType("application/json")
                .content("{\"reason\":\"Transferencia\"}"))
            .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE type = ?", Integer.class,
            "STUDENT_WITHDRAWN")).isZero();
    }

    /** And it comes back out again, by name, for the panel the teacher reads. */
    @Test
    void withdraw_theReasonReadsBackWithWhoDecided() throws Exception {
        mvc.perform(post("/api/students/{id}/withdraw", student)
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType("application/json")
                .content("{\"reason\":\"Transferencia\",\"note\":\"A la U.E. San Martín.\"}"))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/students/{id}", student)
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Withdrawn"))
            .andExpect(jsonPath("$.statusReason").value("Transferencia"))
            .andExpect(jsonPath("$.statusNote").value("A la U.E. San Martín."))
            .andExpect(jsonPath("$.statusChangedById").value(director.toString()))
            .andExpect(jsonPath("$.statusChangedByName").isNotEmpty())
            .andExpect(jsonPath("$.statusChangedAt").isNotEmpty());
    }
}
