package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * One homeroom teacher per course per gestión, as a database guarantee and not only as a service
 * rule: the partial unique index {@code ux_courses_homeroom_per_year} (V24) is what stops a second
 * active course of the same year from naming the same teacher, whichever code path writes it. The
 * JDBC cases drive the index directly because the index is the subject; the HTTP cases prove the
 * service refuses with the friendly message before the index ever has to.
 */
class CourseHomeroomUniquenessIT extends AbstractIntegrationTest {

    private static final Path V24 = Path.of("docs/sql/V24__courses_unique_homeroom_per_year.sql");

    @Autowired private MockMvc mvc;

    private Integer year2027() {
        jdbc.update("INSERT INTO academic_years (year, active) VALUES (2027, false)");
        return academicYearId(2027);
    }

    private ResultActions postCourse(
            String parallelName, UUID homeroomTeacher, UUID assignedTeacher) throws Exception {
        UUID director = seedUser("Director", false);
        Integer gradeId = jdbc.queryForObject("SELECT id_grade FROM grades LIMIT 1", Integer.class);
        Integer parallelId =
                jdbc.queryForObject(
                        "SELECT id_parallel FROM parallels WHERE name = ?",
                        Integer.class,
                        parallelName);
        UUID subjectId = jdbc.queryForObject("SELECT id_subject FROM subjects LIMIT 1", UUID.class);
        Map<String, Object> body =
                Map.of(
                        "id_grade",
                        gradeId,
                        "id_parallel",
                        parallelId,
                        "id_homeroom_teacher",
                        homeroomTeacher.toString(),
                        "assignments",
                        List.of(
                                Map.of(
                                        "id_subject",
                                        subjectId.toString(),
                                        "id_teacher",
                                        assignedTeacher.toString())));
        return mvc.perform(
                post("/api/courses")
                        .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private int courseCount() {
        return jdbc.queryForObject("SELECT count(*) FROM courses", Integer.class);
    }

    @Test
    void postCourse_teacherAlreadyHomeroomThatYear_isRefusedWith409() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        seedCourse(teacher, "A");

        String body =
                postCourse("B", teacher, teacher)
                        .andExpect(status().isConflict())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // The service's own message, not the index's generic 409: the pre-check ran first.
        assertThat(body).contains("Ya es docente de aula");
        assertThat(courseCount()).isEqualTo(1);
    }

    @Test
    void postCourse_teacherWasHomeroomInAnotherYear_isAccepted() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        seedCourse(teacher, "A", year2027());

        postCourse("B", teacher, teacher).andExpect(status().isOk());

        assertThat(courseCount()).isEqualTo(2);
    }

    @Test
    void putActiveTrue_whoseTeacherTookAnotherCourseMeanwhile_isRefusedWith409() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        UUID dormant = seedCourse(teacher, "A");
        jdbc.update("UPDATE courses SET is_active = false WHERE id_course = ?", dormant);
        UUID taken = seedCourse(teacher, "B");
        UUID director = seedUser("Director", false);

        mvc.perform(
                        put("/api/courses/{id}", dormant)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("active", true))))
                .andExpect(status().isConflict());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM courses WHERE id_course = ?",
                                Boolean.class,
                                dormant))
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM courses WHERE id_course = ?",
                                Boolean.class,
                                taken))
                .isTrue();
    }

    @Test
    void database_refusesAnInsertThatDuplicatesAnActiveHomeroomInOneYear() {
        UUID teacher = seedUser("Teacher", false);
        seedCourse(teacher, "A");

        assertThatThrownBy(() -> seedCourse(teacher, "B"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_refusesAnUpdateThatDuplicatesAnActiveHomeroomInOneYear() {
        UUID teacher = seedUser("Teacher", false);
        seedCourse(teacher, "A");
        UUID other = seedCourse(null, "B");

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE courses SET id_homeroom_teacher = ? "
                                                + "WHERE id_course = ?",
                                        teacher,
                                        other))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_refusesReactivatingACourseWhoseTeacherIsHomeroomElsewhere() {
        UUID teacher = seedUser("Teacher", false);
        UUID dormant = seedCourse(teacher, "A");
        jdbc.update("UPDATE courses SET is_active = false WHERE id_course = ?", dormant);
        seedCourse(teacher, "B");

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE courses SET is_active = true WHERE id_course = ?",
                                        dormant))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void database_letsAnInactiveCourseKeepTheSameTeacher() {
        UUID teacher = seedUser("Teacher", false);
        UUID dormant = seedCourse(teacher, "A");
        jdbc.update("UPDATE courses SET is_active = false WHERE id_course = ?", dormant);

        UUID active = seedCourse(teacher, "B");

        assertThat(active).isNotNull();
        assertThat(courseCount()).isEqualTo(2);
    }

    @Test
    void database_letsTheSameTeacherRunOneCoursePerYear() {
        UUID teacher = seedUser("Teacher", false);
        seedCourse(teacher, "A");

        seedCourse(teacher, "A", year2027());

        assertThat(courseCount()).isEqualTo(2);
    }

    @Test
    void database_letsAnyNumberOfCoursesHaveNoHomeroomTeacher() {
        seedCourse(null, "A");
        seedCourse(null, "B");
        seedCourse(null, "C");

        assertThat(courseCount()).isEqualTo(3);
    }

    /**
     * The swap is the one legitimate way two teachers trade courses of one year, and the index is
     * checked per statement, so a naive pair of updates collides in the middle. Proven here against
     * the real index, with the persisted result read back.
     */
    @Test
    void swap_underTheIndex_tradesTheTwoTeachersWithoutACollision() throws Exception {
        UUID teacherA = seedUser("Teacher", false);
        UUID teacherB = seedUser("Teacher", false);
        UUID courseA = seedCourse(teacherA, "A");
        UUID courseB = seedCourse(teacherB, "B");
        UUID director = seedUser("Director", false);

        mvc.perform(
                        put("/api/courses/homeroom-teachers/swap")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "id_course_a", courseA.toString(),
                                                        "id_course_b", courseB.toString()))))
                .andExpect(status().isOk());

        assertThat(homeroomOf(courseA)).isEqualTo(teacherB);
        assertThat(homeroomOf(courseB)).isEqualTo(teacherA);
    }

    private UUID homeroomOf(UUID courseId) {
        return jdbc.queryForObject(
                "SELECT id_homeroom_teacher FROM courses WHERE id_course = ?",
                UUID.class,
                courseId);
    }

    /**
     * V24 itself, run against the IT Postgres. Existing duplicates must abort it with a message
     * that names them (no silent cleanup); once they are fixed it creates the index, and a second
     * run is a no-op.
     */
    @Test
    void v24Script_abortsOnExistingDuplicates_thenCreatesTheIndex_andIsIdempotent()
            throws Exception {
        String script = Files.readString(V24);
        UUID teacher = seedUser("Teacher", false);
        try {
            jdbc.execute("DROP INDEX IF EXISTS ux_courses_homeroom_per_year");
            UUID first = seedCourse(teacher, "A");
            UUID second = seedCourse(teacher, "B");

            assertThatThrownBy(() -> jdbc.execute(script))
                    .isInstanceOfAny(
                            UncategorizedSQLException.class, DataIntegrityViolationException.class)
                    .hasMessageContaining(teacher.toString())
                    .hasMessageContaining(first.toString())
                    .hasMessageContaining(second.toString());
            assertThat(indexExists()).isFalse();

            jdbc.update(
                    "UPDATE courses SET id_homeroom_teacher = NULL WHERE id_course = ?", second);
            jdbc.execute(script);
            assertThat(indexExists()).isTrue();

            jdbc.execute(script);
            assertThat(indexExists()).isTrue();
        } finally {
            jdbc.execute("TRUNCATE TABLE courses CASCADE");
            jdbc.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS ux_courses_homeroom_per_year "
                            + "ON courses (id_homeroom_teacher, id_academic_year) "
                            + "WHERE is_active AND id_homeroom_teacher IS NOT NULL");
        }
    }

    private boolean indexExists() {
        return jdbc.queryForObject(
                        "SELECT count(*) FROM pg_indexes WHERE indexname = 'ux_courses_homeroom_per_year'",
                        Integer.class)
                > 0;
    }
}
