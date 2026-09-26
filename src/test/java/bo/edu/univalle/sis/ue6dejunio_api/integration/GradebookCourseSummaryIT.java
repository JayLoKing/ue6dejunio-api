package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Director's table of the whole school, over a real Postgres.
 *
 * <p>The counting is unit-tested against mocked ports, and those mocks hand back the totals the
 * test itself wrote. What only a database can answer is whether the row a course shows is built
 * from the same {@code total_score} the school's sheets print — a generated column this code never
 * writes — and whether the table really reaches across every classroom of the gestión instead of
 * reading one.
 */
class GradebookCourseSummaryIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;

    @BeforeEach
    void seedSchool() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);

        // Quinto A: one passing at 90, one failing at 47. Average 68.50.
        UUID courseA = seedCourse(teacher, "A");
        UUID langA = seedClassGroup(courseA, teacher, "Lenguaje");
        gradeOneArea(
                seedEnrollment(seedStudent("Ana", "Perez"), courseA),
                langA,
                new BigDecimal("10"),
                new BigDecimal("40"),
                new BigDecimal("35"),
                new BigDecimal("5"));
        gradeOneArea(
                seedEnrollment(seedStudent("Beto", "Quispe"), courseA),
                langA,
                new BigDecimal("5"),
                new BigDecimal("20"),
                new BigDecimal("20"),
                new BigDecimal("2"));

        // Quinto B: one student, nobody graded. It has to appear, and without an average.
        UUID courseB = seedCourse(teacher, "B");
        seedClassGroup(courseB, teacher, "Lenguaje");
        seedEnrollment(seedStudent("Carla", "Rojas"), courseB);
    }

    /** One area graded in the first trimester. {@code total_score} is the database's own sum. */
    private void gradeOneArea(
            UUID enrollmentId,
            UUID classGroupId,
            BigDecimal being,
            BigDecimal knowing,
            BigDecimal doing,
            BigDecimal deciding) {
        jdbc.update(
                "INSERT INTO academic_scores (id_academic_score, id_course_enrollment,"
                        + " id_class_group, trimester, score_being, score_knowing, score_doing,"
                        + " score_deciding) VALUES (?,?,?,1,?,?,?,?)",
                UUID.randomUUID(),
                enrollmentId,
                classGroupId,
                being,
                knowing,
                doing,
                deciding);
    }

    private String summaryAsDirector() throws Exception {
        return mvc.perform(
                        get("/api/gradebook/course-summary")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .param("id_academic_year", String.valueOf(currentAcademicYearId()))
                                .param("trimester", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void courseSummary_reachesEveryClassroomOfTheGestion() throws Exception {
        String body = summaryAsDirector();

        assertThat(JsonPath.<List<String>>read(body, "$[*].parallelName"))
                .containsExactlyInAnyOrder("A", "B");
    }

    /**
     * The course is named {@code courseId}, camelCase, like every other response of this API.
     *
     * <p>Pinned because nothing else pins it. The field names of a response are its contract with
     * the browser, and a rename that no test reads breaks the screen while the suite stays green —
     * which is exactly what happened here once. snake_case belongs to request parameters and
     * bodies; what goes out is camelCase.
     */
    @Test
    void courseSummary_namesTheCourseInCamelCase() throws Exception {
        String body = summaryAsDirector();

        assertThat(JsonPath.<List<String>>read(body, "$[*].courseId")).hasSize(2);
    }

    /**
     * The numbers are the sheets' numbers.
     *
     * <p>90 and 47 are what {@code total_score} computes from those four dimensions, so the course
     * average has to be 68.50 and not a number this endpoint arrived at on its own. 47 is below the
     * pass mark and 90 above it, which is what makes one passed and one failed.
     */
    @Test
    void courseSummary_countsAndAveragesWhatTheSheetsPrint() throws Exception {
        String body = summaryAsDirector();

        assertThat(
                        JsonPath.<List<Integer>>read(body, "$[?(@.parallelName == 'A')].students")
                                .get(0))
                .isEqualTo(2);
        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.parallelName == 'A')].passed").get(0))
                .isEqualTo(1);
        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.parallelName == 'A')].failed").get(0))
                .isEqualTo(1);
        assertThat(JsonPath.<List<Double>>read(body, "$[?(@.parallelName == 'A')].average").get(0))
                .isEqualTo(68.50);
    }

    /**
     * A classroom nobody graded reports no average at all.
     *
     * <p>Not zero: zero says the class failed, and what happened is that the marks have not been
     * entered. The enrolment still counts, because the course exists and is waiting.
     */
    @Test
    void courseSummary_anUngradedClassroomHasNoAverage() throws Exception {
        String body = summaryAsDirector();

        assertThat(
                        JsonPath.<List<Integer>>read(body, "$[?(@.parallelName == 'B')].students")
                                .get(0))
                .isEqualTo(1);
        assertThat(JsonPath.<List<Object>>read(body, "$[?(@.parallelName == 'B')].average"))
                .containsExactly((Object) null);
        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.parallelName == 'B')].passed").get(0))
                .isZero();
        assertThat(JsonPath.<List<Integer>>read(body, "$[?(@.parallelName == 'B')].failed").get(0))
                .isZero();
    }

    /**
     * Director only. It is the whole school's academic standing in one payload, which is exactly
     * what a teacher has no business reading about the classrooms that are not theirs.
     */
    @Test
    void courseSummary_isClosedToATeacher() throws Exception {
        mvc.perform(
                        get("/api/gradebook/course-summary")
                                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                                .param("id_academic_year", String.valueOf(currentAcademicYearId()))
                                .param("trimester", "1"))
                .andExpect(status().isForbidden());
    }

    /**
     * The two counts the Director's course dialog shows, read from the overview endpoint rather
     * than from the (paged) roster listing — a real database is what proves the GROUP BY reaches
     * every active enrolment of the course and not just the students seeded above.
     */
    @Test
    void courseOverview_countsMalesAndFemalesOfTheCourse() throws Exception {
        UUID anotherTeacher = seedUser("Teacher", false);
        UUID course = seedCourse(anotherTeacher, "C");
        seedClassGroup(course, anotherTeacher, "Lenguaje");

        UUID male = seedStudent("Diego", "Flores");
        seedEnrollment(male, course);

        UUID female = seedStudent("Elena", "Mamani");
        jdbc.update("UPDATE students SET gender = 'F' WHERE id_student = ?", female);
        seedEnrollment(female, course);

        // Withdrawn: off the roll, so neither count nor "Total estudiantes" may see her.
        UUID withdrawn = seedStudent("Flora", "Choque");
        jdbc.update("UPDATE students SET gender = 'F' WHERE id_student = ?", withdrawn);
        UUID withdrawnEnrollment = seedEnrollment(withdrawn, course);
        jdbc.update(
                "UPDATE course_enrollments SET status = 'Withdrawn' WHERE id_course_enrollment = ?",
                withdrawnEnrollment);

        String body =
                mvc.perform(
                                get("/api/courses/" + course + "/overview")
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director"))
                                        .param("trimester", "1"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<Integer>read(body, "$.males")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.females")).isEqualTo(1);

        // The three numbers a screen puts side by side, and they add up: two on the roll, one boy
        // and one girl.
        assertThat(JsonPath.<Integer>read(body, "$.activeStudents")).isEqualTo(2);

        // Three, and that is not a contradiction. "students" is the academic roster and keeps the
        // withdrawn girl, because the marks she earned before leaving are still owed to the year's
        // records — see ICourseEnrollmentDomain#studentsByCourse. Pinned so that nobody "fixes"
        // one of these two totals into the other: they answer different questions.
        assertThat(JsonPath.<Integer>read(body, "$.students.total")).isEqualTo(3);
    }

    /** A trimester nobody has graded is an empty table of real classrooms, not an error. */
    @Test
    void courseSummary_aTrimesterNobodyGradedStillListsTheClassrooms() throws Exception {
        String body =
                mvc.perform(
                                get("/api/gradebook/course-summary")
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director"))
                                        .param(
                                                "id_academic_year",
                                                String.valueOf(currentAcademicYearId()))
                                        .param("trimester", "3"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<List<String>>read(body, "$[*].parallelName")).hasSize(2);
        assertThat(JsonPath.<List<Object>>read(body, "$[*].average")).containsOnly((Object) null);
    }
}
