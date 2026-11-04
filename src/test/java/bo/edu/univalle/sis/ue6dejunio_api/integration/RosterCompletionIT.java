package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The full path the school takes: the name-only spreadsheet registers the course, a second import
 * of it adds nobody, and the SIE roster of the course then fills in the same students' RUDE, CI,
 * birth date and gender — written through the persistence adapter over the real schema.
 */
class RosterCompletionIT extends AbstractIntegrationTest {

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

    private static final String SPREADSHEET =
            """
            {"names": "ANA MARIA", "lastNames": "QUISPE MAMANI"},
            {"names": "LUIS", "lastNames": "CONDORI"}
            """;

    private int students() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM students", Integer.class);
    }

    @Test
    void spreadsheetTwiceThenSie_registersOnceAndCompletesTheSameStudents() throws Exception {
        sync(SPREADSHEET)
                .andExpect(jsonPath("$.studentsCreated").value(2))
                .andExpect(jsonPath("$.completed").value(0));
        UUID ana =
                jdbc.queryForObject(
                        "SELECT id_student FROM students WHERE names = 'ANA MARIA'", UUID.class);

        sync(SPREADSHEET)
                .andExpect(jsonPath("$.studentsCreated").value(0))
                .andExpect(jsonPath("$.enrollmentsSkipped").value(2));
        assertThat(students()).isEqualTo(2);

        sync("""
                        {"rudeCode": "80730001", "identityCard": "1234567",
                         "names": "Ana María", "lastNames": "Quispe Mamani",
                         "birthDate": "2015-03-01", "gender": "F"}
                        """)
                .andExpect(jsonPath("$.studentsCreated").value(0))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.conflicts.length()").value(0));

        assertThat(students()).isEqualTo(2);
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT rude_code, identity_card, birth_date, gender, names "
                                + "FROM students WHERE id_student = ?",
                        ana);
        assertThat(row.get("rude_code")).isEqualTo("80730001");
        assertThat(row.get("identity_card")).isEqualTo("1234567");
        assertThat(row.get("birth_date")).hasToString("2015-03-01");
        assertThat(row.get("gender")).isEqualTo("F");
        // The name already on record stays as it was: completion only fills what is missing.
        assertThat(row.get("names")).isEqualTo("ANA MARIA");

        // And the SIE roster read again now resolves by RUDE, completing nothing more.
        sync("""
                        {"rudeCode": "80730001", "identityCard": "1234567",
                         "names": "Ana María", "lastNames": "Quispe Mamani",
                         "birthDate": "2015-03-01", "gender": "F"}
                        """)
                .andExpect(jsonPath("$.studentsCreated").value(0))
                .andExpect(jsonPath("$.completed").value(0));
    }

    /** A name match never reaches into another course: there, the row is a new student. */
    @Test
    void anIncompleteStudentOfAnotherCourse_isNotMatched() throws Exception {
        UUID other = seedCourse(seedUser("Teacher", false), "B");
        UUID luis = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO students (id_student, names, last_names, status) "
                        + "VALUES (?, 'LUIS', 'CONDORI', 'Effective')",
                luis);
        seedEnrollment(luis, other);

        sync("{\"names\": \"LUIS\", \"lastNames\": \"CONDORI\"}")
                .andExpect(jsonPath("$.studentsCreated").value(1))
                .andExpect(jsonPath("$.completed").value(0));
    }
}
