package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Removing a course through DELETE /api/courses/{id}: one that never held students or academic
 * content is erased together with its bare class groups; one that did is only deactivated, and
 * every row it generated stays where it was.
 */
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

    private int count(String sql, UUID id) {
        return jdbc.queryForObject(sql, Integer.class, id);
    }

    private int coursesWithId(UUID course) {
        return count("SELECT count(*) FROM courses WHERE id_course = ?", course);
    }

    private int classGroupsOf(UUID course) {
        return count("SELECT count(*) FROM class_groups WHERE id_course = ?", course);
    }

    @Test
    void delete_ofAnEmptyCourseWithBareClassGroups_erasesTheCourseAndItsClassGroups()
            throws Exception {
        UUID course = seedCourse(teacher, "A");
        seedClassGroup(course, teacher, "Lenguaje");
        seedClassGroup(course, teacher, "Matematicas");

        mvc.perform(delete("/api/courses/" + course).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DELETED"));

        assertThat(coursesWithId(course)).isZero();
        assertThat(classGroupsOf(course)).isZero();
    }

    @Test
    void delete_ofAnEmptyCourseWithoutClassGroups_erasesIt() throws Exception {
        UUID course = seedCourse(null, "A");

        mvc.perform(delete("/api/courses/" + course).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DELETED"));

        assertThat(coursesWithId(course)).isZero();
    }

    @Test
    void delete_withAnEnrolledStudent_deactivatesAndKeepsEveryRow() throws Exception {
        UUID course = seedCourse(teacher, "A");
        seedClassGroup(course, teacher, "Lenguaje");
        UUID enrollment = seedEnrollment(seedStudent(), course);

        mvc.perform(delete("/api/courses/" + course).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DEACTIVATED"));

        assertThat(isActive(course)).isFalse();
        assertThat(classGroupsOf(course)).isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM course_enrollments"
                                        + " WHERE id_course_enrollment = ?",
                                enrollment))
                .isEqualTo(1);
    }

    @Test
    void delete_withCriteriaButNoStudents_deactivatesAndKeepsTheCriteria() throws Exception {
        UUID course = seedCourse(teacher, "A");
        UUID classGroup = seedClassGroup(course, teacher, "Lenguaje");
        UUID criterion = seedCriterion(classGroup, 1, "Knowing", "Pruebas");

        mvc.perform(delete("/api/courses/" + course).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DEACTIVATED"));

        assertThat(isActive(course)).isFalse();
        assertThat(classGroupsOf(course)).isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM evaluation_criteria WHERE id_criterion = ?",
                                criterion))
                .isEqualTo(1);
    }

    @Test
    void delete_withAPedagogicalReportButNoStudents_deactivatesAndKeepsTheReport()
            throws Exception {
        UUID course = seedCourse(teacher, "A");
        jdbc.update(
                "INSERT INTO pedagogical_reports (id_course, trimester, achievements) "
                        + "VALUES (?,1,'x')",
                course);

        mvc.perform(delete("/api/courses/" + course).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DEACTIVATED"));

        assertThat(isActive(course)).isFalse();
        assertThat(count("SELECT count(*) FROM pedagogical_reports WHERE id_course = ?", course))
                .isEqualTo(1);
    }

    @Test
    void delete_ofACourseAlreadyDeactivatedThatHoldsStudents_staysDeactivated() throws Exception {
        UUID course = seedCourse(teacher, "A");
        seedEnrollment(seedStudent(), course);
        jdbc.update("UPDATE courses SET is_active = false WHERE id_course = ?", course);

        mvc.perform(delete("/api/courses/" + course).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DEACTIVATED"));

        assertThat(isActive(course)).isFalse();
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
