package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The school's "NOMINA DE ESTUDIANTES" spreadsheet names students and their course and nothing
 * else. Such a row must create a student with RUDE, CI, birth date and gender empty, through JPA
 * over the real schema, and every read of that student must say the record is incomplete.
 */
class StudentOptionalIdentityIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID teacher;
    private UUID course;

    @BeforeEach
    void seed() {
        teacher = seedUser("Teacher", false);
        course = seedCourse(teacher, "A");
    }

    private String bearer() {
        return "Bearer " + tokenFor(teacher, "Teacher");
    }

    @Test
    void nameOnlyRows_createIncompleteStudents_andEveryReadFlagsThem() throws Exception {
        mvc.perform(
                        post("/api/course-enrollments/sync")
                                .header("Authorization", bearer())
                                .contentType("application/json")
                                .content(
                                        """
                                        {"id_course": "%s", "students": [
                                          {"names": "ANA", "lastNames": "QUISPE MAMANI",
                                           "rudeCode": "", "identityCard": null},
                                          {"names": "LUIS", "lastNames": "CONDORI"}
                                        ]}
                                        """
                                                .formatted(course)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsCreated").value(2))
                .andExpect(jsonPath("$.enrollmentsCreated").value(2));

        // Two students with no RUDE and no CI: the UNIQUE constraints admit any number of NULLs.
        Map<String, Object> ana =
                jdbc.queryForMap(
                        "SELECT id_student, rude_code, identity_card, birth_date, gender "
                                + "FROM students WHERE names = 'ANA'");
        assertThat(ana.get("rude_code")).isNull();
        assertThat(ana.get("identity_card")).isNull();
        assertThat(ana.get("birth_date")).isNull();
        assertThat(ana.get("gender")).isNull();
        UUID anaId = (UUID) ana.get("id_student");

        mvc.perform(
                        get("/api/course-enrollments")
                                .param("id_course", course.toString())
                                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].incomplete").value(true))
                .andExpect(jsonPath("$.content[1].incomplete").value(true));

        mvc.perform(get("/api/students/" + anaId).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rudeCode").isEmpty())
                .andExpect(jsonPath("$.birthDate").isEmpty())
                .andExpect(jsonPath("$.incomplete").value(true));

        mvc.perform(
                        get("/api/students/search")
                                .param("q", "quispe")
                                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].incomplete").value(true));
    }

    @Test
    void aStudentWithEveryField_isNotIncomplete() throws Exception {
        UUID student = seedStudent("Maria", "Lopez");
        seedEnrollment(student, course);

        mvc.perform(get("/api/students/" + student).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomplete").value(false));

        mvc.perform(get("/api/students/search").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].incomplete").value(false));

        mvc.perform(
                        get("/api/course-enrollments")
                                .param("id_course", course.toString())
                                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].incomplete").value(false));
    }
}
