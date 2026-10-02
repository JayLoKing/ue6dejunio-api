package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The Director prepares next year by copying a year's classrooms and subjects into it: the same
 * grades, parallels and subjects, with every teacher left blank to confirm one by one.
 *
 * <p>The 2026 school has 1ro A (two subjects), 1ro B (one active, one closed subject) and 1ro C,
 * which is itself closed and so must not travel.
 */
class YearStructureDuplicationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID teacher;
    private Integer id2026;
    private Integer id2027;

    @BeforeEach
    void seedTheSchool() {
        teacher = seedUser("Teacher", false);
        id2026 = academicYearId(2026);
        UUID courseA = seedCourse(teacher, "A");
        UUID courseB = seedCourse(null, "B");
        UUID courseC = seedCourse(null, "C");
        jdbc.update("UPDATE courses SET is_active = false WHERE id_course = ?", courseC);
        seedClassGroup(courseA, teacher, "Matematicas");
        seedClassGroup(courseA, teacher, "Lenguaje");
        seedClassGroup(courseB, teacher, "Matematicas");
        UUID closedSubject = seedClassGroup(courseB, teacher, "Lenguaje");
        jdbc.update(
                "UPDATE class_groups SET is_active = false WHERE id_class_group = ?",
                closedSubject);
        seedClassGroup(courseC, teacher, "Matematicas");
        jdbc.update("INSERT INTO academic_years (year, active) VALUES (2027, false)");
        id2027 = academicYearId(2027);
    }

    private String bearer(String role) {
        return "Bearer " + tokenFor(seedUser(role, false), role);
    }

    private ResultActions duplicate(String authorization, Object targetId, Object sourceId)
            throws Exception {
        var request =
                post("/api/academic-years/" + targetId + "/duplicate-structure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsString(
                                        Map.of("source_academic_year_id", sourceId)));
        if (authorization != null) {
            request = request.header("Authorization", authorization);
        }
        return mvc.perform(request);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int classGroupsOf(Integer yearId) {
        return count(
                "SELECT count(*) FROM class_groups cg JOIN courses c USING (id_course)"
                        + " WHERE c.id_academic_year = ?",
                yearId);
    }

    @Test
    void copiesTheActiveCoursesAndSubjects_withNoTeachers() throws Exception {
        duplicate(bearer("Director"), id2027, id2026)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesCreated").value(2))
                .andExpect(jsonPath("$.coursesSkipped").value(0))
                .andExpect(jsonPath("$.classGroupsCreated").value(3))
                .andExpect(jsonPath("$.skippedCourses").isEmpty());

        assertThat(
                        jdbc.queryForList(
                                "SELECT p.name FROM courses c JOIN parallels p USING (id_parallel)"
                                        + " WHERE c.id_academic_year = ? AND c.is_active"
                                        + " ORDER BY p.name",
                                String.class,
                                id2027))
                .containsExactly("A", "B");
        assertThat(
                        count(
                                "SELECT count(*) FROM courses WHERE id_academic_year = ?"
                                        + " AND id_homeroom_teacher IS NOT NULL",
                                id2027))
                .isZero();
        assertThat(
                        jdbc.queryForList(
                                "SELECT p.name || ':' || s.name FROM class_groups cg"
                                        + " JOIN courses c USING (id_course)"
                                        + " JOIN parallels p USING (id_parallel)"
                                        + " JOIN subjects s USING (id_subject)"
                                        + " WHERE c.id_academic_year = ? AND cg.is_active"
                                        + " AND cg.id_teacher IS NULL ORDER BY 1",
                                String.class,
                                id2027))
                .containsExactly("A:Lenguaje", "A:Matematicas", "B:Matematicas");
        assertThat(classGroupsOf(id2027)).isEqualTo(3);
    }

    @Test
    void theSourceYearIsLeftUntouched() throws Exception {
        duplicate(bearer("Director"), id2027, id2026).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM courses WHERE id_academic_year = ?", id2026))
                .isEqualTo(3);
        assertThat(
                        count(
                                "SELECT count(*) FROM courses WHERE id_academic_year = ?"
                                        + " AND id_homeroom_teacher = ?",
                                id2026,
                                teacher))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM class_groups cg JOIN courses c USING"
                                        + " (id_course) WHERE c.id_academic_year = ?"
                                        + " AND cg.id_teacher = ?",
                                id2026,
                                teacher))
                .isEqualTo(5);
    }

    @Test
    void runningItAgain_skipsEverythingAndCreatesNothing() throws Exception {
        duplicate(bearer("Director"), id2027, id2026).andExpect(status().isOk());

        duplicate(bearer("Director"), id2027, id2026)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesCreated").value(0))
                .andExpect(jsonPath("$.coursesSkipped").value(2))
                .andExpect(jsonPath("$.classGroupsCreated").value(0))
                .andExpect(jsonPath("$.skippedCourses[0]").value("Primero A"))
                .andExpect(jsonPath("$.skippedCourses[1]").value("Primero B"));

        assertThat(count("SELECT count(*) FROM courses WHERE id_academic_year = ?", id2027))
                .isEqualTo(2);
        assertThat(classGroupsOf(id2027)).isEqualTo(3);
    }

    @Test
    void aCourseThatAlreadyExistsInTheTarget_isSkippedAndKeptAsItWas() throws Exception {
        UUID existing = seedCourse(null, "A", id2027);
        seedClassGroup(existing, null, "Matematicas");

        duplicate(bearer("Director"), id2027, id2026)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesCreated").value(1))
                .andExpect(jsonPath("$.coursesSkipped").value(1))
                .andExpect(jsonPath("$.classGroupsCreated").value(1))
                .andExpect(jsonPath("$.skippedCourses[0]").value("Primero A"));

        assertThat(count("SELECT count(*) FROM class_groups WHERE id_course = ?", existing))
                .isEqualTo(1);
    }

    @Test
    void aClosedYearCanBeTheSource() throws Exception {
        jdbc.update(
                "UPDATE academic_years SET closed_at = now() WHERE id_academic_year = ?", id2026);

        duplicate(bearer("Director"), id2027, id2026)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coursesCreated").value(2));
    }

    @Test
    void aClosedTarget_isRefusedWith409_andNothingIsCreated() throws Exception {
        jdbc.update(
                "UPDATE academic_years SET closed_at = now() WHERE id_academic_year = ?", id2027);

        duplicate(bearer("Director"), id2027, id2026).andExpect(status().isConflict());

        assertThat(count("SELECT count(*) FROM courses WHERE id_academic_year = ?", id2027))
                .isZero();
    }

    @Test
    void sameYearAsSourceAndTarget_is400() throws Exception {
        duplicate(bearer("Director"), id2026, id2026).andExpect(status().isBadRequest());

        assertThat(count("SELECT count(*) FROM courses WHERE id_academic_year = ?", id2026))
                .isEqualTo(3);
    }

    @Test
    void unknownTargetOrSource_is404() throws Exception {
        duplicate(bearer("Director"), 999999, id2026).andExpect(status().isNotFound());
        duplicate(bearer("Director"), id2027, 999999).andExpect(status().isNotFound());
    }

    @Test
    void missingSource_is400() throws Exception {
        mvc.perform(
                        post("/api/academic-years/" + id2027 + "/duplicate-structure")
                                .header("Authorization", bearer("Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheDirector_mayDuplicate() throws Exception {
        duplicate(bearer("Teacher"), id2027, id2026).andExpect(status().isForbidden());
        duplicate(bearer("Secretary"), id2027, id2026).andExpect(status().isForbidden());
        duplicate(null, id2027, id2026).andExpect(status().isUnauthorized());

        assertThat(count("SELECT count(*) FROM courses WHERE id_academic_year = ?", id2027))
                .isZero();
    }

    @Test
    void theHomeroomOf2026_canRunACopiedCourseOf2027_butOnlyOne() throws Exception {
        duplicate(bearer("Director"), id2027, id2026).andExpect(status().isOk());
        List<UUID> copies =
                jdbc.queryForList(
                        "SELECT c.id_course FROM courses c JOIN parallels p USING (id_parallel)"
                                + " WHERE c.id_academic_year = ? ORDER BY p.name",
                        UUID.class,
                        id2027);
        String director = bearer("Director");

        putHomeroom(director, copies.get(0)).andExpect(status().isOk());
        putHomeroom(director, copies.get(1)).andExpect(status().isConflict());
    }

    private ResultActions putHomeroom(String authorization, UUID courseId) throws Exception {
        return mvc.perform(
                put("/api/courses/" + courseId + "/homeroom-teacher")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsString(
                                        Map.of("id_homeroom_teacher", teacher.toString()))));
    }
}
