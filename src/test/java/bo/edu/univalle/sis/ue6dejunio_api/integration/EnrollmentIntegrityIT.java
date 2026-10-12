package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
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
 * What a roster import refuses to do to the students it already knows: seat one twice in the same
 * gestión, or pick one of two students when the file's RUDE and CI point at different people.
 *
 * <p>Driven through the endpoints over a real Postgres, because each rule reads seats across the
 * courses of a year and a mocked port would only prove the loop reads its answer.
 */
class EnrollmentIntegrityIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID teacher;
    private UUID target;
    private UUID sibling;

    @BeforeEach
    void seed() {
        teacher = seedUser("Teacher", false);
        target = seedCourse(teacher, "A");
        sibling = seedCourse(seedUser("Teacher", false), "B");
    }

    private String rowOf(UUID student) {
        return jdbc.queryForObject(
                "SELECT '{\"rudeCode\": \"' || rude_code || '\", \"identityCard\": \"' || identity_card"
                        + " || '\", \"names\": \"' || names || '\", \"lastNames\": \"' || last_names"
                        + " || '\", \"birthDate\": \"2015-01-01\", \"gender\": \"M\"}' "
                        + "FROM students WHERE id_student = ?",
                String.class,
                student);
    }

    private ResultActions sync(UUID courseId, String... rows) throws Exception {
        return mvc.perform(
                        post("/api/course-enrollments/sync")
                                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                                .contentType("application/json")
                                .content(
                                        """
                                        {"id_course": "%s", "students": [%s]}
                                        """
                                                .formatted(courseId, String.join(",", rows))))
                .andExpect(status().isOk());
    }

    private String labelOf(UUID course) {
        return jdbc.queryForObject(
                "SELECT g.name || ' ' || p.name FROM courses c "
                        + "JOIN grades g ON g.id_grade = c.id_grade "
                        + "JOIN parallels p ON p.id_parallel = c.id_parallel "
                        + "WHERE c.id_course = ?",
                String.class,
                course);
    }

    private Integer rowsIn(UUID student, UUID course) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_enrollments WHERE id_student = ? AND id_course = ?",
                Integer.class,
                student,
                course);
    }

    private String rudeOf(UUID student) {
        return jdbc.queryForObject(
                "SELECT rude_code FROM students WHERE id_student = ?", String.class, student);
    }

    /** Two courses of one gestión: the second import leaves the student where they already sit. */
    @Test
    void studentSeatedInASiblingCourse_isLeftOutAndNamedWithThatCourse() throws Exception {
        UUID student = seedStudent("Ana", "Quispe");
        seedEnrollment(student, sibling);

        sync(target, rowOf(student))
                .andExpect(jsonPath("$.enrollmentsCreated").value(0))
                .andExpect(jsonPath("$.skippedOtherCourse.length()").value(1))
                .andExpect(jsonPath("$.skippedOtherCourse[0].studentId").value(student.toString()))
                .andExpect(jsonPath("$.skippedOtherCourse[0].rudeCode").value(rudeOf(student)))
                .andExpect(jsonPath("$.skippedOtherCourse[0].fullName").value("Ana Quispe"))
                .andExpect(jsonPath("$.skippedOtherCourse[0].courseLabel").value(labelOf(sibling)));

        assertThat(rowsIn(student, target)).isZero();
    }

    /** A row whose RUDE is one student's and whose CI is another's enrols neither. */
    @Test
    void rudeOfOneStudentAndCardOfAnother_isLeftOutAsAConflict() throws Exception {
        UUID ana = seedStudent("Ana", "Quispe");
        UUID luis = seedStudent("Luis", "Mamani");
        String cardOfLuis =
                jdbc.queryForObject(
                        "SELECT identity_card FROM students WHERE id_student = ?",
                        String.class,
                        luis);
        String mixed =
                """
                {"rudeCode": "%s", "identityCard": "%s", "names": "Ana", "lastNames": "Quispe",
                 "birthDate": "2015-01-01", "gender": "F"}
                """
                        .formatted(rudeOf(ana), cardOfLuis);

        sync(target, mixed)
                .andExpect(jsonPath("$.enrollmentsCreated").value(0))
                .andExpect(jsonPath("$.studentsCreated").value(0))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].rudeCode").value(rudeOf(ana)))
                .andExpect(jsonPath("$.conflicts[0].identityCard").value(cardOfLuis))
                .andExpect(jsonPath("$.conflicts[0].fullName").value("Ana Quispe"))
                .andExpect(
                        jsonPath("$.conflicts[0].reason")
                                .value(
                                        allOf(
                                                containsString("Ana Quispe"),
                                                containsString("Luis Mamani"))));

        assertThat(rowsIn(ana, target)).isZero();
        assertThat(rowsIn(luis, target)).isZero();
    }

    /** Typed in by hand, the same student is refused with a 409 that names the course. */
    @Test
    void singleEnroll_ofAStudentSeatedInASiblingCourse_isAConflictNamingTheCourse()
            throws Exception {
        UUID student = seedStudent("Ana", "Quispe");
        seedEnrollment(student, sibling);

        mvc.perform(
                        post("/api/course-enrollments")
                                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                                .contentType("application/json")
                                .content(
                                        """
                                        {"id_course": "%s", "student": %s}
                                        """
                                                .formatted(target, rowOf(student))))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "El estudiante ya está inscrito en "
                                                + labelOf(sibling)
                                                + " de esta gestión."));

        assertThat(rowsIn(student, target)).isZero();
    }

    /** A seat in last year's course is history, not a seat taken this year. */
    @Test
    void studentSeatedInACourseOfLastYear_isEnrolled() throws Exception {
        UUID student = seedStudent("Ana", "Quispe");
        seedEnrollment(student, seedCourse(seedUser("Teacher", false), "B", academicYearId(2025)));

        sync(target, rowOf(student))
                .andExpect(jsonPath("$.enrollmentsCreated").value(1))
                .andExpect(jsonPath("$.skippedOtherCourse.length()").value(0));

        assertThat(rowsIn(student, target)).isEqualTo(1);
    }
}
