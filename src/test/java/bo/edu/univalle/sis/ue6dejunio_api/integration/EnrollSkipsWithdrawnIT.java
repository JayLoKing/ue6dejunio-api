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
 * A roster load never puts back a student the school had withdrawn.
 *
 * <p>Only the Director's explicit decision does that, so the load leaves the student alone, loads
 * everyone else, and names who it left out. Driven through the endpoints because the guarantee
 * spans the students table, the enrolment rows and the response, and {@code UNIQUE (id_student,
 * id_course)} means the closed enrolment row is the one a careless load would reopen.
 */
class EnrollSkipsWithdrawnIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private UUID course;
    private UUID student;

    private static final String RUDE = "1234567890123";
    private static final String CARNET = "ID-READMIT";
    private static final String OTHER_RUDE = "9999999999999";
    private static final String OTHER_CARNET = "ID-OTHER";

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

    private static String studentJson(String rude, String card, String names, String lastNames) {
        return """
            {"rudeCode": "%s", "identityCard": "%s", "names": "%s", "lastNames": "%s",
             "birthDate": "2015-01-01", "gender": "F"}
            """
                .formatted(rude, card, names, lastNames);
    }

    private String rosterWith(UUID courseId, String... students) {
        return """
            {"id_course": "%s", "students": [%s]}
            """
                .formatted(courseId, String.join(",", students));
    }

    private void withdraw() throws Exception {
        mvc.perform(
                        post("/api/students/{id}/withdraw", student)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType("application/json")
                                .content("{\"reason\":\"Transferencia\"}"))
                .andExpect(status().isNoContent());
    }

    private ResultActions syncAs(UUID user, String role, UUID courseId, String... students)
            throws Exception {
        return mvc.perform(
                        post("/api/course-enrollments/sync")
                                .header("Authorization", "Bearer " + tokenFor(user, role))
                                .contentType("application/json")
                                .content(rosterWith(courseId, students)))
                .andExpect(status().isOk());
    }

    private String statusOfStudent() {
        return jdbc.queryForObject(
                "SELECT status FROM students WHERE id_student = ?", String.class, student);
    }

    private Integer effectiveEnrolments() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_enrollments WHERE id_student = ? AND status = 'Effective'",
                Integer.class,
                student);
    }

    private String ana() {
        return studentJson(RUDE, CARNET, "Ana", "Quispe");
    }

    /** The case the Director's own withdrawal exists for: a teacher must not be able to undo it. */
    @Test
    void teacherLoad_leavesAWithdrawnStudentWithdrawn_andNamesThem() throws Exception {
        withdraw();

        syncAs(teacher, "Teacher", course, ana())
                .andExpect(jsonPath("$.skippedWithdrawn.length()").value(1))
                .andExpect(jsonPath("$.skippedWithdrawn[0].studentId").value(student.toString()))
                .andExpect(jsonPath("$.skippedWithdrawn[0].fullName").value("Ana Quispe"))
                .andExpect(jsonPath("$.enrollmentsCreated").value(0))
                .andExpect(jsonPath("$.studentsTotal").value(1));

        assertThat(statusOfStudent()).isEqualTo("Withdrawn");
        assertThat(effectiveEnrolments()).isZero();
    }

    /** No exception for the Director: the way back is the explicit reinstatement, not a load. */
    @Test
    void directorLoad_alsoSkipsAWithdrawnStudent() throws Exception {
        withdraw();

        syncAs(director, "Director", course, ana())
                .andExpect(jsonPath("$.skippedWithdrawn[0].studentId").value(student.toString()));

        assertThat(statusOfStudent()).isEqualTo("Withdrawn");
        assertThat(effectiveEnrolments()).isZero();
    }

    /** Skipping must not reopen the closed row nor add a second one beside it. */
    @Test
    void skippedStudent_keepsTheirSingleClosedEnrolmentRow() throws Exception {
        withdraw();

        syncAs(director, "Director", course, ana());

        Integer rows =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM course_enrollments WHERE id_student = ? AND id_course = ?",
                        Integer.class,
                        student,
                        course);
        assertThat(rows).isEqualTo(1);
    }

    /** Skipped in another course too: nothing is created there for them. */
    @Test
    void skippedInAnotherCourse_getsNoEnrolmentThere() throws Exception {
        withdraw();
        UUID otherCourse = seedCourse(seedUser("Teacher", false), "B");

        syncAs(director, "Director", otherCourse, ana())
                .andExpect(jsonPath("$.skippedWithdrawn.length()").value(1));

        Integer rows =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM course_enrollments WHERE id_student = ? AND id_course = ?",
                        Integer.class,
                        student,
                        otherCourse);
        assertThat(rows).isZero();
        assertThat(statusOfStudent()).isEqualTo("Withdrawn");
    }

    /** The rest of the load proceeds: only the withdrawn student is left out. */
    @Test
    void restOfTheLoad_proceeds() throws Exception {
        withdraw();
        String newcomer = studentJson(OTHER_RUDE, OTHER_CARNET, "Luis", "Mamani");

        syncAs(teacher, "Teacher", course, ana(), newcomer)
                .andExpect(jsonPath("$.studentsTotal").value(2))
                .andExpect(jsonPath("$.studentsCreated").value(1))
                .andExpect(jsonPath("$.enrollmentsCreated").value(1))
                .andExpect(jsonPath("$.skippedWithdrawn.length()").value(1));

        Integer newcomerSeats =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM course_enrollments ce JOIN students s "
                                + "ON s.id_student = ce.id_student "
                                + "WHERE s.rude_code = ? AND ce.status = 'Effective'",
                        Integer.class,
                        OTHER_RUDE);
        assertThat(newcomerSeats).isEqualTo(1);
    }

    /** The single-student endpoint follows the same rule as the roster sync. */
    @Test
    void singleEnroll_skipsAWithdrawnStudent() throws Exception {
        withdraw();

        mvc.perform(
                        post("/api/course-enrollments")
                                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                                .contentType("application/json")
                                .content(
                                        """
                                        {"id_course": "%s", "student": %s}
                                        """
                                                .formatted(course, ana())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skippedWithdrawn[0].studentId").value(student.toString()));

        assertThat(statusOfStudent()).isEqualTo("Withdrawn");
    }

    /** The student stays out of the directory: the load did not make them visible again. */
    @Test
    void skippedStudent_staysOutOfTheDirectory() throws Exception {
        withdraw();

        syncAs(director, "Director", course, ana());

        mvc.perform(
                        get("/api/students/search")
                                .param("q", "Quispe")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    /** A load with nobody withdrawn reports an empty list, not a missing field. */
    @Test
    void studentStillOnTheRoll_reportsNoSkippedWithdrawn() throws Exception {
        syncAs(director, "Director", course, ana())
                .andExpect(jsonPath("$.skippedWithdrawn.length()").value(0))
                .andExpect(jsonPath("$.studentsReadmitted").doesNotExist());

        assertThat(statusOfStudent()).isEqualTo("Effective");
    }
}
