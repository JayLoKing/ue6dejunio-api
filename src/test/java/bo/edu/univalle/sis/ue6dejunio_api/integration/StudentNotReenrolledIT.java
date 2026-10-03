package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Who sat in last year's classrooms and has no seat this year, over a real Postgres.
 *
 * <p>The whole answer is one anti-join, so what is pinned here is what it includes and what it
 * leaves out: any seat in the reference year (whatever its status) removes a student from the list,
 * a student who only ever left last year was never "effective" there, and a child with two seats
 * last year is one row.
 */
class StudentNotReenrolledIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private Integer year2025;
    private Integer year2026;
    private UUID course2025A;
    private UUID course2026A;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        year2025 = academicYearId(2025);
        year2026 = academicYearId(2026);
        course2025A = seedCourse(teacher, "A", year2025);
        course2026A = seedCourse(teacher, "A", year2026);
    }

    private UUID enrolled(String names, String lastNames, UUID course, String enrolmentStatus) {
        UUID student = seedStudent(names, lastNames);
        seat(student, course, enrolmentStatus);
        return student;
    }

    private void seat(UUID student, UUID course, String enrolmentStatus) {
        UUID enrolment = seedEnrollment(student, course);
        jdbc.update(
                "UPDATE course_enrollments SET status = ? WHERE id_course_enrollment = ?",
                enrolmentStatus,
                enrolment);
    }

    private ResultActions reportAs(UUID actor, String role, Integer yearId) throws Exception {
        var request =
                get("/api/students/not-reenrolled")
                        .header("Authorization", "Bearer " + tokenFor(actor, role));
        if (yearId != null) {
            request = request.param("id_academic_year", String.valueOf(yearId));
        }
        return mvc.perform(request);
    }

    private String body(ResultActions result) throws Exception {
        return result.andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private List<String> names(String body) {
        return JsonPath.read(body, "$.students[*].fullName");
    }

    /** The core of the report: last year's effective students with no seat this year. */
    @Test
    void listsLastYearsEffectiveStudentsWithNoSeatThisYear() throws Exception {
        UUID stayed = enrolled("Ana", "Sigue", course2025A, "Effective");
        seat(stayed, course2026A, "Effective");
        enrolled("Beto", "Falta", course2025A, "Effective");

        String body = body(reportAs(director, "Director", null));

        assertThat(names(body)).containsExactly("Beto Falta");
        assertThat(JsonPath.<Integer>read(body, "$.academicYear.year")).isEqualTo(2026);
        assertThat(JsonPath.<Integer>read(body, "$.previousYear.year")).isEqualTo(2025);
        assertThat(JsonPath.<Integer>read(body, "$.previousYear.id")).isEqualTo(year2025);
    }

    /** A seat this year, whatever its status, is an answer: they did come back. */
    @Test
    void aWithdrawnSeatThisYearStillCountsAsHavingComeBack() throws Exception {
        UUID student = enrolled("Ana", "Vuelve", course2025A, "Effective");
        seat(student, course2026A, "Withdrawn");

        assertThat(names(body(reportAs(director, "Director", null)))).isEmpty();
    }

    /** Someone who had already left by last year's end was never an effective student there. */
    @Test
    void aStudentWhoOnlyHeldAWithdrawnSeatLastYear_isNotListed() throws Exception {
        enrolled("Carla", "Retirada", course2025A, "Withdrawn");

        assertThat(names(body(reportAs(director, "Director", null)))).isEmpty();
    }

    /**
     * Withdrawn after last year ended is a legitimate "did not come back": kept, with the status
     * the student carries now so the screen can say so.
     */
    @Test
    void aStudentWithdrawnSinceIsListedWithTheirCurrentStatus() throws Exception {
        UUID student = enrolled("Dino", "Baja", course2025A, "Effective");
        jdbc.update("UPDATE students SET status = 'Withdrawn' WHERE id_student = ?", student);

        String body = body(reportAs(director, "Director", null));

        assertThat(names(body)).containsExactly("Dino Baja");
        assertThat(JsonPath.<String>read(body, "$.students[0].status")).isEqualTo("Withdrawn");
    }

    /** Two effective seats last year are one child, reported under their latest seat. */
    @Test
    void aStudentWithTwoSeatsLastYear_isOneRowAtTheLatestSeat() throws Exception {
        UUID courseB = seedCourse(seedUser("Teacher", false), "B", year2025);
        UUID student = enrolled("Eva", "Traslado", course2025A, "Effective");
        seat(student, courseB, "Effective");
        jdbc.update(
                "UPDATE course_enrollments SET enrollment_date = '2025-02-01' WHERE id_course = ?",
                course2025A);
        jdbc.update(
                "UPDATE course_enrollments SET enrollment_date = '2025-06-01' WHERE id_course = ?",
                courseB);

        String body = body(reportAs(director, "Director", null));

        assertThat(names(body)).containsExactly("Eva Traslado");
        assertThat(JsonPath.<String>read(body, "$.students[0].lastCourse.parallel")).isEqualTo("B");
        assertThat(JsonPath.<String>read(body, "$.students[0].lastCourse.id"))
                .isEqualTo(courseB.toString());
    }

    @Test
    void eachRowCarriesTheIdentifiersAndTheLabelOfTheLastCourse() throws Exception {
        UUID student = enrolled("Fabio", "Dato", course2025A, "Effective");

        String body = body(reportAs(director, "Director", null));

        assertThat(JsonPath.<String>read(body, "$.students[0].studentId"))
                .isEqualTo(student.toString());
        assertThat(JsonPath.<String>read(body, "$.students[0].rudeCode")).startsWith("RUDE-");
        assertThat(JsonPath.<String>read(body, "$.students[0].identityCard")).startsWith("ID-");
        assertThat(JsonPath.<String>read(body, "$.students[0].lastCourse.id"))
                .isEqualTo(course2025A.toString());
        assertThat(JsonPath.<String>read(body, "$.students[0].lastCourse.name"))
                .endsWith(" A")
                .isEqualTo(JsonPath.<String>read(body, "$.students[0].lastCourse.grade") + " A");
    }

    /** Ordered by the course they came from, then by surname and name. */
    @Test
    void rowsAreSortedByLastCourseThenName() throws Exception {
        UUID courseB = seedCourse(seedUser("Teacher", false), "B", year2025);
        enrolled("Zoe", "Zeta", course2025A, "Effective");
        enrolled("Ana", "Zeta", course2025A, "Effective");
        enrolled("Beto", "Alfa", course2025A, "Effective");
        enrolled("Ada", "Alfa", courseB, "Effective");

        assertThat(names(body(reportAs(director, "Director", null))))
                .containsExactly("Beto Alfa", "Ana Zeta", "Zoe Zeta", "Ada Alfa");
    }

    /** Naming a year makes it the reference: 2025's report is against 2024. */
    @Test
    void anExplicitYearIsTheReferenceYear() throws Exception {
        jdbc.update("INSERT INTO academic_years (year) VALUES (2024)");
        UUID course2024 = seedCourse(teacher, "A", academicYearId(2024));
        enrolled("Gael", "Antiguo", course2024, "Effective");
        // Present in 2025, so he is not missing from the 2025 roll.
        UUID back = enrolled("Hana", "Vuelta", course2024, "Effective");
        seat(back, course2025A, "Effective");

        String body = body(reportAs(director, "Director", year2025));

        assertThat(names(body)).containsExactly("Gael Antiguo");
        assertThat(JsonPath.<Integer>read(body, "$.academicYear.year")).isEqualTo(2025);
        assertThat(JsonPath.<Integer>read(body, "$.previousYear.year")).isEqualTo(2024);
    }

    /** The first gestión on record has nothing before it: an empty list, not an error. */
    @Test
    void withNoPreviousYear_theListIsEmptyAndPreviousYearIsNull() throws Exception {
        enrolled("Ana", "Primera", course2025A, "Effective");

        String body = body(reportAs(director, "Director", year2025));

        assertThat(names(body)).isEmpty();
        assertThat(JsonPath.<Object>read(body, "$.previousYear")).isNull();
        assertThat(JsonPath.<Integer>read(body, "$.academicYear.year")).isEqualTo(2025);
    }

    @Test
    void anUnknownYear_isNotFound() throws Exception {
        reportAs(director, "Director", 987654).andExpect(status().isNotFound());
    }

    @Test
    void isOpenToTheSecretariat() throws Exception {
        UUID secretary = seedUser("Secretary", false);

        reportAs(secretary, "Secretary", null).andExpect(status().isOk());
    }

    /** A teacher has no scope over students of courses that are not theirs. */
    @Test
    void isClosedToATeacher() throws Exception {
        reportAs(teacher, "Teacher", null).andExpect(status().isForbidden());
    }
}
