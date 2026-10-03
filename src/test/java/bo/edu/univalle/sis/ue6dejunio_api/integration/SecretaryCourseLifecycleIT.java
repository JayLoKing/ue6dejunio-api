package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The secretariat runs the course lifecycle the Director runs: creating a course with its subjects
 * and teachers, editing it, reassigning its homeroom teacher, deactivating it, and copying a year's
 * structure into the next. Everything around that stays the Director's, and the writes the
 * secretariat has never had (attendance, enrolments, withdrawals) stay closed.
 */
class SecretaryCourseLifecycleIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID teacher;
    private UUID otherTeacher;
    private String secretary;

    @BeforeEach
    void seed() {
        teacher = seedUser("Teacher", false);
        otherTeacher = seedUser("Teacher", false);
        secretary = "Bearer " + tokenFor(seedUser("Secretary", false), "Secretary");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String auth, Object body)
            throws Exception {
        MockHttpServletRequestBuilder r = request.header("Authorization", auth);
        if (body != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(r);
    }

    private Map<String, Object> newCourseBody() {
        Integer gradeId = jdbc.queryForObject("SELECT id_grade FROM grades LIMIT 1", Integer.class);
        Integer parallelId =
                jdbc.queryForObject(
                        "SELECT id_parallel FROM parallels WHERE name = 'B'", Integer.class);
        UUID subjectId =
                jdbc.queryForObject(
                        "SELECT id_subject FROM subjects WHERE name = 'Matematicas'", UUID.class);
        return Map.of(
                "id_grade",
                gradeId,
                "id_parallel",
                parallelId,
                "id_homeroom_teacher",
                teacher.toString(),
                "assignments",
                List.of(
                        Map.of(
                                "id_subject",
                                subjectId.toString(),
                                "id_teacher",
                                otherTeacher.toString())));
    }

    @Test
    void secretary_createsACourseWithItsSubjectsAndTeachers() throws Exception {
        send(post("/api/courses"), secretary, newCourseBody()).andExpect(status().isOk());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM class_groups cg JOIN courses c USING"
                                        + " (id_course) WHERE c.id_homeroom_teacher = ?",
                                Integer.class,
                                teacher))
                .isEqualTo(1);
    }

    @Test
    void secretary_editsACourse() throws Exception {
        UUID course = seedCourse(null, "A");
        send(put("/api/courses/" + course), secretary, Map.of("active", true))
                .andExpect(status().isOk());
    }

    @Test
    void secretary_reassignsTheHomeroomTeacherOfOneCourse() throws Exception {
        UUID course = seedCourse(null, "A");
        send(
                        put("/api/courses/" + course + "/homeroom-teacher"),
                        secretary,
                        Map.of("id_homeroom_teacher", teacher.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void secretary_deactivatesACourse() throws Exception {
        UUID course = seedCourse(null, "A");
        send(delete("/api/courses/" + course), secretary, null)
                .andExpect(status().is2xxSuccessful());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM courses WHERE id_course = ?",
                                Boolean.class,
                                course))
                .isFalse();
    }

    @Test
    void secretary_duplicatesTheStructureOfAnotherYear() throws Exception {
        UUID course = seedCourse(null, "A");
        seedClassGroup(course, teacher, "Matematicas");
        jdbc.update("INSERT INTO academic_years (year, active) VALUES (2027, false)");
        send(
                        post(
                                "/api/academic-years/"
                                        + academicYearId(2027)
                                        + "/duplicate-structure"),
                        secretary,
                        Map.of("source_academic_year_id", academicYearId(2026)))
                .andExpect(status().isOk());
    }

    @Test
    void secretary_stillCannotSwapHomeroomTeachers() throws Exception {
        UUID a = seedCourse(teacher, "A");
        UUID b = seedCourse(otherTeacher, "B");
        send(
                        put("/api/courses/homeroom-teachers/swap"),
                        secretary,
                        Map.of("id_course_a", a.toString(), "id_course_b", b.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void secretary_stillCannotReassignAClassGroupTeacher() throws Exception {
        UUID course = seedCourse(teacher, "A");
        UUID classGroup = seedClassGroup(course, teacher, "Matematicas");
        send(
                        put("/api/courses/" + course + "/class-groups/" + classGroup + "/teacher"),
                        secretary,
                        Map.of("teacherId", otherTeacher.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void secretary_stillCannotTouchTheRestOfTheCatalogue() throws Exception {
        for (String path :
                List.of(
                        "/api/subjects",
                        "/api/trimester-periods",
                        "/api/knowledge-areas",
                        "/api/levels",
                        "/api/grades",
                        "/api/parallels",
                        "/api/users")) {
            send(post(path), secretary, Map.of()).andExpect(status().isForbidden());
        }
        UUID id = UUID.randomUUID();
        send(put("/api/subjects/" + id), secretary, Map.of()).andExpect(status().isForbidden());
        send(delete("/api/subjects/" + id), secretary, null).andExpect(status().isForbidden());
        send(put("/api/academic-years/1/activate"), secretary, null)
                .andExpect(status().isForbidden());
        send(put("/api/academic-years/1/close"), secretary, null).andExpect(status().isForbidden());
        send(put("/api/academic-years/1/reopen"), secretary, null)
                .andExpect(status().isForbidden());
    }

    @Test
    void secretary_createsAnAcademicYear_andRepeatingItReturnsTheExistingOne() throws Exception {
        send(post("/api/academic-years"), secretary, Map.of("year", 2031))
                .andExpect(status().isCreated());
        send(post("/api/academic-years"), secretary, Map.of("year", 2031))
                .andExpect(status().isOk());
    }

    @Test
    void secretary_stillCannotWriteAttendanceEnrolmentsOrWithdrawals() throws Exception {
        send(post("/api/attendance/daily"), secretary, Map.of()).andExpect(status().isForbidden());
        send(post("/api/course-enrollments/sync"), secretary, Map.of())
                .andExpect(status().isForbidden());
        send(post("/api/scores/anything"), secretary, Map.of()).andExpect(status().isForbidden());
        send(post("/api/students/" + UUID.randomUUID() + "/withdraw"), secretary, Map.of())
                .andExpect(status().isForbidden());
    }
}
