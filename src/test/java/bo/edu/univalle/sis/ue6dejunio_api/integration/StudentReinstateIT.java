package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * POST /api/students/{id}/reinstate: the Director's way of putting a withdrawn student back. Driven
 * through the endpoints because the guarantee spans the students row, the enrolment rows and the
 * directory, and {@code UNIQUE (id_student, id_course)} decides whether a row is reopened or made.
 */
class StudentReinstateIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private UUID secretary;
    private UUID course;
    private UUID student;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        secretary = seedUser("Secretary", false);
        course = seedCourse(teacher, "A");
        student = seedStudent();
        seedEnrollment(student, course);
    }

    private void withdraw() throws Exception {
        mvc.perform(
                        post("/api/students/{id}/withdraw", student)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType("application/json")
                                .content("{\"reason\":\"Transferencia\"}"))
                .andExpect(status().isNoContent());
    }

    private ResultActions reinstateAs(UUID user, String role, String body) throws Exception {
        return mvc.perform(
                post("/api/students/{id}/reinstate", student)
                        .header("Authorization", "Bearer " + tokenFor(user, role))
                        .contentType("application/json")
                        .content(body));
    }

    private String studentStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM students WHERE id_student = ?", String.class, student);
    }

    private String enrollmentStatus(UUID courseId) {
        return jdbc.queryForObject(
                "SELECT status FROM course_enrollments WHERE id_student = ? AND id_course = ?",
                String.class,
                student,
                courseId);
    }

    private int enrollmentRows() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_enrollments WHERE id_student = ?",
                Integer.class,
                student);
    }

    @Test
    void reinstate_withASeatThisYear_reopensItAndRestoresTheStudent() throws Exception {
        withdraw();

        reinstateAs(director, "Director", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(student.toString()))
                .andExpect(jsonPath("$.status").value("Effective"))
                .andExpect(jsonPath("$.courseId").value(course.toString()))
                .andExpect(jsonPath("$.enrollmentReactivated").value(true));

        assertThat(studentStatus()).isEqualTo("Effective");
        assertThat(enrollmentStatus(course)).isEqualTo("Effective");
        assertThat(enrollmentRows()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status_reason FROM students WHERE id_student = ?",
                                String.class,
                                student))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status_note FROM students WHERE id_student = ?",
                                String.class,
                                student))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status_changed_by FROM students WHERE id_student = ?",
                                UUID.class,
                                student))
                .isEqualTo(director);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status_changed_at FROM students WHERE id_student = ?",
                                Object.class,
                                student))
                .isNotNull();

        mvc.perform(
                        get("/api/students/search")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + student + "')]").isNotEmpty());
    }

    @Test
    void reinstate_namingTheSameCourseAsTheSeat_isFine() throws Exception {
        withdraw();

        reinstateAs(director, "Director", "{\"id_course\":\"" + course + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentReactivated").value(true));
    }

    @Test
    void reinstate_namingAnotherCourseThanTheSeat_returns400() throws Exception {
        UUID other = seedCourse(seedUser("Teacher", false), "B");
        withdraw();

        reinstateAs(director, "Director", "{\"id_course\":\"" + other + "\"}")
                .andExpect(status().isBadRequest());

        assertThat(studentStatus()).isEqualTo("Withdrawn");
        assertThat(enrollmentStatus(course)).isEqualTo("Withdrawn");
    }

    @Test
    void reinstate_pastYearWithdrawal_seatsTheStudentInTheNamedCourse() throws Exception {
        // The student left in 2025 and has no seat this year.
        UUID pastCourse = seedCourse(teacher, "B", academicYearId(2025));
        jdbc.update("DELETE FROM course_enrollments WHERE id_student = ?", student);
        seedEnrollment(student, pastCourse);
        jdbc.update(
                "UPDATE course_enrollments SET status = 'Withdrawn' WHERE id_student = ?", student);
        jdbc.update("UPDATE students SET status = 'Withdrawn' WHERE id_student = ?", student);

        reinstateAs(director, "Director", "{\"id_course\":\"" + course + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(course.toString()))
                .andExpect(jsonPath("$.enrollmentReactivated").value(false));

        assertThat(studentStatus()).isEqualTo("Effective");
        assertThat(enrollmentStatus(course)).isEqualTo("Effective");
        // The past year's row is history and stays as it was.
        assertThat(enrollmentStatus(pastCourse)).isEqualTo("Withdrawn");
        assertThat(enrollmentRows()).isEqualTo(2);
    }

    @Test
    void reinstate_withoutASeatAndNoCourse_returns400() throws Exception {
        UUID pastCourse = seedCourse(teacher, "B", academicYearId(2025));
        jdbc.update("DELETE FROM course_enrollments WHERE id_student = ?", student);
        seedEnrollment(student, pastCourse);
        jdbc.update(
                "UPDATE course_enrollments SET status = 'Withdrawn' WHERE id_student = ?", student);
        jdbc.update("UPDATE students SET status = 'Withdrawn' WHERE id_student = ?", student);

        reinstateAs(director, "Director", "{}").andExpect(status().isBadRequest());

        assertThat(studentStatus()).isEqualTo("Withdrawn");
    }

    @Test
    void reinstate_withACourseOfAnotherYear_returns400() throws Exception {
        UUID pastCourse = seedCourse(teacher, "B", academicYearId(2025));
        jdbc.update("DELETE FROM course_enrollments WHERE id_student = ?", student);
        jdbc.update("UPDATE students SET status = 'Withdrawn' WHERE id_student = ?", student);

        reinstateAs(director, "Director", "{\"id_course\":\"" + pastCourse + "\"}")
                .andExpect(status().isBadRequest());

        assertThat(studentStatus()).isEqualTo("Withdrawn");
        assertThat(enrollmentRows()).isZero();
    }

    @Test
    void reinstate_withAnUnknownCourse_returns404() throws Exception {
        jdbc.update("DELETE FROM course_enrollments WHERE id_student = ?", student);
        jdbc.update("UPDATE students SET status = 'Withdrawn' WHERE id_student = ?", student);

        reinstateAs(director, "Director", "{\"id_course\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void reinstate_unknownStudent_returns404() throws Exception {
        mvc.perform(
                        post("/api/students/{id}/reinstate", UUID.randomUUID())
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reinstate_studentNotWithdrawn_returns409() throws Exception {
        reinstateAs(director, "Director", "{}").andExpect(status().isConflict());
    }

    @Test
    void reinstate_teacherRefused() throws Exception {
        withdraw();

        reinstateAs(teacher, "Teacher", "{}").andExpect(status().isForbidden());

        assertThat(studentStatus()).isEqualTo("Withdrawn");
    }

    @Test
    void reinstate_secretaryRefused() throws Exception {
        withdraw();

        reinstateAs(secretary, "Secretary", "{}").andExpect(status().isForbidden());

        assertThat(studentStatus()).isEqualTo("Withdrawn");
    }

    @Test
    void reinstate_closedYear_returns409() throws Exception {
        withdraw();
        jdbc.update("UPDATE academic_years SET closed_at = now() WHERE active");

        reinstateAs(director, "Director", "{}").andExpect(status().isConflict());

        assertThat(studentStatus()).isEqualTo("Withdrawn");
        assertThat(enrollmentStatus(course)).isEqualTo("Withdrawn");
    }

    private int notificationsOfType(String type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type = ?", Integer.class, type);
    }

    /**
     * The whole chain: the Director reinstates, and the teachers of the course the student is back
     * in hear about it. An integration test because what could break is the query that finds them
     * and the listener running after commit.
     */
    @Test
    void reinstate_tellsTheHomeroomAndTheTechnicalTeacherOfThatCourse() throws Exception {
        UUID technical = seedUser("Teacher", false);
        seedClassGroup(course, technical, "Matematicas");
        withdraw();

        reinstateAs(director, "Director", "{}").andExpect(status().isOk());

        for (UUID receiver : new UUID[] {teacher, technical}) {
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT message FROM notifications WHERE receiver_id = ? AND type = ?",
                                    String.class,
                                    receiver,
                                    "STUDENT_REINSTATED"))
                    .startsWith("Reincorporación: ")
                    .contains(" vuelve a ");
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT resource_id FROM notifications WHERE receiver_id = ? AND type = ?",
                                UUID.class,
                                teacher,
                                "STUDENT_REINSTATED"))
                .isEqualTo(student);
        assertThat(notificationsOfType("STUDENT_REINSTATED")).isEqualTo(2);
    }

    /** A refused reinstatement announces nothing: the student is still off the roll. */
    @Test
    void reinstate_refused_tellsNobody() throws Exception {
        withdraw();

        reinstateAs(director, "Director", "{\"id_course\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isBadRequest());
        reinstateAs(teacher, "Teacher", "{}").andExpect(status().isForbidden());

        assertThat(notificationsOfType("STUDENT_REINSTATED")).isZero();
    }
}
