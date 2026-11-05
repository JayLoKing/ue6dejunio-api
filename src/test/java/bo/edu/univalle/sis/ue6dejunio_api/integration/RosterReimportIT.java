package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
 * The order the school reported: the SIE roster seats the course complete, the name-only
 * spreadsheet of the same course follows, and the SIE roster is loaded once more. Neither second
 * load may register anybody, and the last one must say the students are already enrolled rather
 * than returning.
 */
class RosterReimportIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID teacher;
    private UUID course;

    @BeforeEach
    void seed() {
        teacher = seedUser("Teacher", false);
        course = seedCourse(teacher, "A");
    }

    private ResultActions sync(String rows) throws Exception {
        return mvc.perform(
                        post("/api/course-enrollments/sync")
                                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                                .contentType("application/json")
                                .content(
                                        """
                                        {"id_course": "%s", "students": [%s]}
                                        """
                                                .formatted(course, rows)))
                .andExpect(status().isOk());
    }

    private static final String SIE =
            """
            {"rudeCode": "80730001", "identityCard": "1234567",
             "names": "Ana María", "lastNames": "Quispe Mamani",
             "birthDate": "2015-03-01", "gender": "F"},
            {"rudeCode": "80730002", "identityCard": "7654321",
             "names": "Luis", "lastNames": "Condori",
             "birthDate": "2015-06-01", "gender": "M"}
            """;

    private static final String SPREADSHEET =
            """
            {"names": "ANA MARIA", "lastNames": "QUISPE MAMANI"},
            {"names": "LUIS", "lastNames": "CONDORI"}
            """;

    private int students() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM students", Integer.class);
    }

    private int seatsInCourse() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM course_enrollments WHERE id_course = ?",
                Integer.class,
                course);
    }

    private int studentsWithMoreThanOneSeat() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT id_student FROM course_enrollments"
                        + " WHERE id_course = ? GROUP BY id_student HAVING COUNT(*) > 1) t",
                Integer.class,
                course);
    }

    @Test
    void sieThenSpreadsheetThenSie_registersEachChildOnce() throws Exception {
        sync(SIE)
                .andExpect(jsonPath("$.studentsCreated").value(2))
                .andExpect(jsonPath("$.enrollmentsCreated").value(2));
        assertThat(students()).isEqualTo(2);

        sync(SPREADSHEET)
                .andExpect(jsonPath("$.studentsCreated").value(0))
                .andExpect(jsonPath("$.enrollmentsCreated").value(0))
                .andExpect(jsonPath("$.enrollmentsSkipped").value(2))
                .andExpect(jsonPath("$.conflicts.length()").value(0));
        assertThat(students()).isEqualTo(2);
        assertThat(seatsInCourse()).isEqualTo(2);
        assertThat(studentsWithMoreThanOneSeat()).isZero();

        sync(SIE)
                .andExpect(jsonPath("$.studentsCreated").value(0))
                .andExpect(jsonPath("$.enrollmentsCreated").value(0))
                .andExpect(jsonPath("$.enrollmentsSkipped").value(2))
                .andExpect(jsonPath("$.studentsFromPreviousYear").value(0))
                .andExpect(jsonPath("$.studentsReentering").value(0))
                .andExpect(jsonPath("$.reinstated.length()").value(0));
        assertThat(students()).isEqualTo(2);
        assertThat(seatsInCourse()).isEqualTo(2);
    }
}
