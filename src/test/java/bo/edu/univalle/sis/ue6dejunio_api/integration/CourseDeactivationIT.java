package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Taking a course out of service is refused while it holds students or academic content. */
class CourseDeactivationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID teacher;
    private String bearer;

    @BeforeEach
    void seed() {
        UUID director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        bearer = "Bearer " + tokenFor(director, "Director");
    }

    private boolean isActive(UUID course) {
        return jdbc.queryForObject(
                "SELECT is_active FROM courses WHERE id_course = ?", Boolean.class, course);
    }

    @Test
    void delete_withAnEnrolledStudent_isRefusedAndTheCourseStaysActive() throws Exception {
        UUID course = seedCourse(teacher, "A");
        seedEnrollment(seedStudent(), course);

        mvc.perform(delete("/api/courses/" + course).header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("inscritos")));

        assertThat(isActive(course)).isTrue();
    }

    @Test
    void delete_ofACourseWithOnlyBareClassGroups_succeeds() throws Exception {
        UUID course = seedCourse(teacher, "A");
        seedClassGroup(course, teacher, "Lenguaje");

        mvc.perform(delete("/api/courses/" + course).header("Authorization", bearer))
                .andExpect(status().isNoContent());

        assertThat(isActive(course)).isFalse();
    }

    @Test
    void delete_withCriteriaButNoStudents_isRefused() throws Exception {
        UUID course = seedCourse(teacher, "A");
        UUID classGroup = seedClassGroup(course, teacher, "Lenguaje");
        seedCriterion(classGroup, 1, "Knowing", "Pruebas");

        mvc.perform(delete("/api/courses/" + course).header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("académicos")));

        assertThat(isActive(course)).isTrue();
    }

    @Test
    void delete_withAPedagogicalReportButNoStudents_isRefused() throws Exception {
        UUID course = seedCourse(teacher, "A");
        jdbc.update(
                "INSERT INTO pedagogical_reports (id_course, trimester, achievements) "
                        + "VALUES (?,1,'x')",
                course);

        mvc.perform(delete("/api/courses/" + course).header("Authorization", bearer))
                .andExpect(status().isConflict());

        assertThat(isActive(course)).isTrue();
    }

    @Test
    void put_deactivatingACourseWithAStudent_isRefused() throws Exception {
        UUID course = seedCourse(teacher, "A");
        seedEnrollment(seedStudent(), course);

        mvc.perform(
                        put("/api/courses/" + course)
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isConflict());

        assertThat(isActive(course)).isTrue();
    }
}
