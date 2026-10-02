package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A teacher runs one classroom per gestión, not one for life: the same person is homeroom of 5A in
 * 2026 and of 5A in 2027, but never of two courses inside one year.
 */
class HomeroomPerYearIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ICourseDomain courseDomain;

    private Integer year2027() {
        jdbc.update("INSERT INTO academic_years (year, active) VALUES (2027, false)");
        return academicYearId(2027);
    }

    private ResultActions assignHomeroom(UUID courseId, UUID teacherId) throws Exception {
        UUID director = seedUser("Director", false);
        return mvc.perform(
                put("/api/courses/" + courseId + "/homeroom-teacher")
                        .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsString(
                                        Map.of("id_homeroom_teacher", teacherId.toString()))));
    }

    @Test
    void homeroomOf2026_canBeHomeroomOf2027() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        seedCourse(teacher, "A");
        UUID next = seedCourse(null, "A", year2027());

        assignHomeroom(next, teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeroomTeacherId").value(teacher.toString()))
                .andExpect(jsonPath("$.year").value(2027));
    }

    @Test
    void withinOneYear_aSecondHomeroomIsStillRefused() throws Exception {
        Integer id2027 = year2027();
        UUID teacher = seedUser("Teacher", false);
        seedCourse(teacher, "A", id2027);
        UUID other = seedCourse(null, "B", id2027);

        assignHomeroom(other, teacher).andExpect(status().isConflict());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT id_homeroom_teacher FROM courses WHERE id_course = ?",
                                UUID.class,
                                other))
                .isNull();
    }

    @Test
    void homeroomOf2026_isStillRefusedForAnother2026Course() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        seedCourse(teacher, "A");
        UUID other = seedCourse(null, "B");

        assignHomeroom(other, teacher).andExpect(status().isConflict());
    }

    @Test
    void whoseClassroomIsIt_prefersTheActiveYearOverANewerOne() {
        UUID teacher = seedUser("Teacher", false);
        UUID active = seedCourse(teacher, "A");
        seedCourse(teacher, "A", year2027());

        assertThat(courseDomain.homeroomCourseOf(teacher).map(Course::id)).contains(active);
    }

    @Test
    void whoseClassroomIsIn_aGivenYear_ignoresTheOthers() {
        UUID teacher = seedUser("Teacher", false);
        UUID in2026 = seedCourse(teacher, "A");
        Integer id2027 = year2027();
        UUID in2027 = seedCourse(teacher, "B", id2027);

        assertThat(courseDomain.homeroomCourseOf(teacher, id2027).map(Course::id)).contains(in2027);
        assertThat(courseDomain.homeroomCourseOf(teacher, currentAcademicYearId()).map(Course::id))
                .contains(in2026);
    }
}
