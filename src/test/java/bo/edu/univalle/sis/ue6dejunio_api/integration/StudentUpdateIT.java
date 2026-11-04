package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * PUT /api/students/{id}: the Director or the homeroom teacher of the student's course this gestión
 * completes a student registered from the name-only roster. Driven over real tokens and the real
 * schema, because both the guard and the UNIQUE codes live there.
 */
class StudentUpdateIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID homeroom;
    private UUID course;
    private UUID student;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        homeroom = seedUser("Teacher", false);
        course = seedCourse(homeroom, "A");
        student = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO students (id_student, names, last_names, status) "
                        + "VALUES (?, 'ANA', 'QUISPE', 'Effective')",
                student);
        seedEnrollment(student, course);
    }

    private static final String COMPLETE =
            """
            {"rudeCode": "80730001", "identityCard": "1234567", "names": "Ana",
             "lastNames": "Quispe", "birthDate": "2015-03-01", "gender": "F"}
            """;

    private ResultActions edit(UUID caller, String role, UUID id, String body) throws Exception {
        return mvc.perform(
                put("/api/students/" + id)
                        .header("Authorization", "Bearer " + tokenFor(caller, role))
                        .contentType("application/json")
                        .content(body));
    }

    @Test
    void homeroomTeacher_completesTheStudent() throws Exception {
        edit(homeroom, "Teacher", student, COMPLETE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rudeCode").value("80730001"))
                .andExpect(jsonPath("$.names").value("Ana"))
                .andExpect(jsonPath("$.incomplete").value(false));

        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT rude_code, identity_card, birth_date, gender, status "
                                + "FROM students WHERE id_student = ?",
                        student);
        assertThat(row.get("rude_code")).isEqualTo("80730001");
        assertThat(row.get("identity_card")).isEqualTo("1234567");
        assertThat(row.get("birth_date")).hasToString("2015-03-01");
        assertThat(row.get("gender")).isEqualTo("F");
        assertThat(row.get("status")).isEqualTo("Effective");
    }

    @Test
    void director_editsAnyStudent_andMayClearAField() throws Exception {
        edit(director, "Director", student, COMPLETE).andExpect(status().isOk());

        edit(director, "Director", student, "{\"names\": \"Ana\", \"lastNames\": \"Quispe\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rudeCode").isEmpty())
                .andExpect(jsonPath("$.incomplete").value(true));
    }

    @Test
    void aRudeAnotherStudentHolds_is409_andNothingIsWritten() throws Exception {
        UUID other = seedStudent("Luis", "Mamani");
        String taken =
                jdbc.queryForObject(
                        "SELECT rude_code FROM students WHERE id_student = ?", String.class, other);

        edit(
                        director,
                        "Director",
                        student,
                        "{\"names\": \"Ana\", \"lastNames\": \"Quispe\", \"rudeCode\": \"%s\"}"
                                .formatted(taken))
                .andExpect(status().isConflict());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT rude_code FROM students WHERE id_student = ?",
                                String.class,
                                student))
                .isNull();
    }

    @Test
    void aTeacherOfAnotherCourse_is403() throws Exception {
        UUID stranger = seedUser("Teacher", false);
        seedCourse(stranger, "B");

        edit(stranger, "Teacher", student, COMPLETE).andExpect(status().isForbidden());
    }

    @Test
    void aSubjectTeacherOfTheCourse_is403() throws Exception {
        UUID technical = seedUser("Teacher", true);
        seedClassGroup(course, technical, "Matematicas");

        edit(technical, "Teacher", student, COMPLETE).andExpect(status().isForbidden());
    }

    @Test
    void secretary_is403() throws Exception {
        UUID secretary = seedUser("Secretary", false);

        edit(secretary, "Secretary", student, COMPLETE).andExpect(status().isForbidden());
    }

    @Test
    void anUnknownStudent_is404ForTheDirector() throws Exception {
        edit(director, "Director", UUID.randomUUID(), COMPLETE).andExpect(status().isNotFound());
    }
}
