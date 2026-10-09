package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The two centralizers as a technical teacher reads them, over a real Postgres.
 *
 * <p>The guard widened from homeroom-only to any teacher of the course, so what keeps the rest of
 * the sheet private is the narrowing in the service. This drives both together: a technical teacher
 * gets in and sees their subject's column only, the homeroom teacher and the Director still see the
 * whole sheet, and a teacher with nothing in the course is still turned away.
 */
class GradebookCentralizerScopeIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID homeroomTeacher;
    private UUID technicalTeacher;
    private UUID unrelatedTeacher;
    private UUID course;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        homeroomTeacher = seedUser("Teacher", false);
        technicalTeacher = seedUser("Teacher", true);
        unrelatedTeacher = seedUser("Teacher", false);

        course = seedCourse(homeroomTeacher, "A");
        UUID math = seedClassGroup(course, homeroomTeacher, "Matematicas");
        UUID language = seedClassGroup(course, technicalTeacher, "Lenguaje");
        UUID enrollment = seedEnrollment(seedStudent("Ana", "Perez"), course);
        // 80 and 60: a general average of 70 that the technical teacher must not see.
        grade(enrollment, math, new BigDecimal("40"), new BigDecimal("40"));
        grade(enrollment, language, new BigDecimal("30"), new BigDecimal("30"));
    }

    /** Trimester 1. {@code total_score} is the database's own sum of the four dimensions. */
    private void grade(UUID enrollmentId, UUID classGroupId, BigDecimal knowing, BigDecimal doing) {
        jdbc.update(
                "INSERT INTO academic_scores (id_academic_score, id_course_enrollment, id_class_group, "
                        + "trimester, score_being, score_knowing, score_doing, score_deciding) "
                        + "VALUES (?,?,?,1,0,?,?,0)",
                UUID.randomUUID(),
                enrollmentId,
                classGroupId,
                knowing,
                doing);
    }

    @Test
    void centralizer_technicalTeacher_readsOnlyTheirSubjectAndNoGeneralAverage() throws Exception {
        String body = read("/api/gradebook/centralizer", technicalTeacher, "Teacher", true);

        assertThat(JsonPath.<List<String>>read(body, "$.content[0].subjects[*].subjectName"))
                .containsExactly("Lenguaje");
        assertThat(firstRow(body).get("generalAverage")).isNull();
    }

    @Test
    void centralizer_homeroomTeacher_keepsTheWholeSheet() throws Exception {
        String body = read("/api/gradebook/centralizer", homeroomTeacher, "Teacher", true);

        assertThat(JsonPath.<List<String>>read(body, "$.content[0].subjects[*].subjectName"))
                .containsExactlyInAnyOrder("Matematicas", "Lenguaje");
        assertThat(new BigDecimal(firstRow(body).get("generalAverage").toString()))
                .isEqualByComparingTo("70.00");
    }

    @Test
    void centralizer_director_keepsTheWholeSheet() throws Exception {
        String body = read("/api/gradebook/centralizer", director, "Director", true);

        assertThat(JsonPath.<List<String>>read(body, "$.content[0].subjects[*].subjectName"))
                .hasSize(2);
    }

    @Test
    void centralizer_teacherWithNothingInTheCourse_isForbidden() throws Exception {
        mvc.perform(
                        get("/api/gradebook/centralizer")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(unrelatedTeacher, "Teacher"))
                                .param("id_course", course.toString())
                                .param("trimester", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void annualCentralizer_technicalTeacher_readsOnlyTheirAreaAndNoAverageAcrossSubjects()
            throws Exception {
        String body = read("/api/gradebook/annual-centralizer", technicalTeacher, "Teacher", false);

        assertThat(JsonPath.<List<String>>read(body, "$.content[0].subjects[*].subjectName"))
                .containsExactly("Lenguaje");
        assertThat(
                        new BigDecimal(
                                JsonPath.read(body, "$.content[0].subjects[0].average").toString()))
                .isEqualByComparingTo("60.00");
        assertThat(firstRow(body).get("finalAverage")).isNull();
        assertThat(JsonPath.<List<Object>>read(body, "$.content[0].trimesterAverages"))
                .containsOnlyNulls();
    }

    @Test
    void annualCentralizer_homeroomTeacher_keepsTheWholeYear() throws Exception {
        String body = read("/api/gradebook/annual-centralizer", homeroomTeacher, "Teacher", false);

        assertThat(JsonPath.<List<String>>read(body, "$.content[0].subjects[*].subjectName"))
                .hasSize(2);
        assertThat(new BigDecimal(firstRow(body).get("finalAverage").toString()))
                .isEqualByComparingTo("70.00");
    }

    @Test
    void annualCentralizer_teacherWithNothingInTheCourse_isForbidden() throws Exception {
        mvc.perform(
                        get("/api/gradebook/annual-centralizer")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(unrelatedTeacher, "Teacher"))
                                .param("id_course", course.toString()))
                .andExpect(status().isForbidden());
    }

    private String read(String path, UUID user, String role, boolean withTrimester)
            throws Exception {
        MockHttpServletRequestBuilder request =
                get(path)
                        .header("Authorization", "Bearer " + tokenFor(user, role))
                        .param("id_course", course.toString());
        if (withTrimester) {
            request.param("trimester", "1");
        }
        return mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** As a map, so a field the serializer left out and one it wrote as null read the same. */
    private static Map<String, Object> firstRow(String body) {
        return JsonPath.read(body, "$.content[0]");
    }
}
