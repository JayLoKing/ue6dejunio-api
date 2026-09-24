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

/**
 * Putting back a student the school had taken off the roll.
 *
 * <p>Driven through the endpoints rather than the repository, because what broke here was not one
 * query: the import wrote a live enrolment while {@code students.status} stayed 'Withdrawn', and
 * {@code UNIQUE (id_student, id_course)} means the way back into the same course is to reopen the
 * row that recorded them leaving it, not to add another. Both only show up against a real database.
 */
class StudentReadmissionIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private UUID course;
    private UUID student;

    private static final String RUDE = "1234567890123";
    private static final String CARNET = "ID-READMIT";

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        course = seedCourse(teacher, "A");
        student = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO students (id_student, rude_code, identity_card, names, last_names, "
                        + "birth_date, gender, status) VALUES (?,?,?,?,?,?,?,?)",
                student,
                RUDE,
                CARNET,
                "Ana",
                "Quispe",
                java.sql.Date.valueOf("2015-01-01"),
                "F",
                "Effective");
        seedEnrollment(student, course);
    }

    private String rosterWith(UUID courseId) {
        return """
            {"id_course": "%s", "students": [{
              "rudeCode": "%s", "identityCard": "%s", "names": "Ana", "lastNames": "Quispe",
              "birthDate": "2015-01-01", "gender": "F"}]}
            """
                .formatted(courseId, RUDE, CARNET);
    }

    private void withdraw() throws Exception {
        mvc.perform(
                        post("/api/students/{id}/withdraw", student)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType("application/json")
                                .content("{\"reason\":\"Transferencia\"}"))
                .andExpect(status().isNoContent());
    }

    private String reimportInto(UUID courseId) throws Exception {
        return mvc.perform(
                        post("/api/course-enrollments/sync")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType("application/json")
                                .content(rosterWith(courseId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String statusOfStudent() {
        return jdbc.queryForObject(
                "SELECT status FROM students WHERE id_student = ?", String.class, student);
    }

    /**
     * The same course. This is the one the unique index makes impossible to fix by inserting: the
     * only row available is the closed one, so the import has to reopen it.
     */
    @Test
    void reimportedIntoTheSameCourse_isBackOnTheRollAndBackInTheCourse() throws Exception {
        withdraw();
        assertThat(statusOfStudent()).isEqualTo("Withdrawn");

        String body = reimportInto(course);

        assertThat(statusOfStudent()).isEqualTo("Effective");
        assertThat(body).contains("\"studentsReadmitted\":1");
        Integer active =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM course_enrollments WHERE id_student = ? AND status = 'Effective'",
                        Integer.class,
                        student);
        assertThat(active).isEqualTo(1);
    }

    /** And still exactly one row: reopening, not duplicating. */
    @Test
    void reimportedIntoTheSameCourse_leavesOneEnrolmentRow() throws Exception {
        withdraw();

        reimportInto(course);

        Integer rows =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM course_enrollments WHERE id_student = ? AND id_course = ?",
                        Integer.class,
                        student,
                        course);
        assertThat(rows).isEqualTo(1);
    }

    /**
     * The half that made the two screens disagree: the enrolment was live, so the teacher saw the
     * student in the course, while the directory filters on the student's own status and did not.
     */
    @Test
    void readmittedStudent_isVisibleInTheDirectoryAgain() throws Exception {
        withdraw();

        mvc.perform(
                        get("/api/students/search")
                                .param("q", "Quispe")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        reimportInto(course);

        mvc.perform(
                        get("/api/students/search")
                                .param("q", "Quispe")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("Effective"))
                .andExpect(jsonPath("$.content[0].parallel").value("A"));
    }

    /**
     * Another course is an ordinary enrolment, but the student still has to come back on the roll.
     */
    @Test
    void reimportedIntoAnotherCourse_isAlsoPutBackOnTheRoll() throws Exception {
        withdraw();
        UUID otherCourse = seedCourse(seedUser("Teacher", false), "B");

        String body = reimportInto(otherCourse);

        assertThat(statusOfStudent()).isEqualTo("Effective");
        assertThat(body).contains("\"studentsReadmitted\":1");
    }

    /**
     * The explanation described an absence that is over; kept, it would describe a student who
     * attends.
     */
    @Test
    void readmission_clearsTheWithdrawalExplanation() throws Exception {
        withdraw();

        reimportInto(course);

        mvc.perform(
                        get("/api/students/{id}", student)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Effective"))
                .andExpect(jsonPath("$.statusReason").doesNotExist())
                .andExpect(jsonPath("$.statusNote").doesNotExist());
    }

    /** Who put them back is recorded, the same as who took them off. */
    @Test
    void readmission_recordsWhoDecidedIt() throws Exception {
        withdraw();

        reimportInto(course);

        UUID changedBy =
                jdbc.queryForObject(
                        "SELECT status_changed_by FROM students WHERE id_student = ?",
                        UUID.class,
                        student);
        assertThat(changedBy).isEqualTo(director);
    }

    /**
     * A student who never left is not "readmitted" — the import must not report a change it did not
     * make.
     */
    @Test
    void studentStillOnTheRoll_isNotReportedAsReadmitted() throws Exception {
        String body = reimportInto(course);

        assertThat(body).contains("\"studentsReadmitted\":0");
        assertThat(statusOfStudent()).isEqualTo("Effective");
    }
}
