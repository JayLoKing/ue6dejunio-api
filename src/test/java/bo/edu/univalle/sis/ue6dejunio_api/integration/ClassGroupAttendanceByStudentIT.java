package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/gradebook/attendance/session/by-student against Postgres: a technical teacher's
 * per-student attendance report of their own subject.
 *
 * <p>The course report counts the daily rows; this one counts one class group's session rows, and
 * nothing else changes — same roll, same trimester periods (T1 Feb-May for 2026 in {@code
 * schema-it.sql}), same percentage rule. The tests seed all three kinds of row on the same days, so
 * a query that picked up the wrong ones would move a number.
 */
class ClassGroupAttendanceByStudentIT extends AbstractIntegrationTest {

    private static final String PATH = "/api/gradebook/attendance/session/by-student";

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID homeroomTeacher;
    private UUID technicalTeacher;
    private UUID unrelatedTeacher;
    private UUID mathTeacher;
    private UUID language;
    private UUID math;
    private UUID anaEnrollment;
    private UUID luisEnrollment;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        homeroomTeacher = seedUser("Teacher", false);
        technicalTeacher = seedUser("Teacher", true);
        unrelatedTeacher = seedUser("Teacher", false);
        // Teaches the other subject of the same course, and is not its homeroom teacher.
        mathTeacher = seedUser("Teacher", true);
        UUID course = seedCourse(homeroomTeacher, "A");
        language = seedClassGroup(course, technicalTeacher, "Lenguaje");
        math = seedClassGroup(course, mathTeacher, "Matematicas");
        // Seeded out of alphabetical order on purpose: the report must sort them itself.
        luisEnrollment = seedEnrollment(seedStudent("Luis", "Zambrana"), course);
        anaEnrollment = seedEnrollment(seedStudent("Ana", "Aguilar"), course);
    }

    private void seedMark(UUID enrollmentId, UUID classGroupId, LocalDate date, String status) {
        jdbc.update(
                "INSERT INTO attendance (id_attendance, id_course_enrollment, id_class_group, date, status) "
                        + "VALUES (?,?,?,?,?)",
                UUID.randomUUID(),
                enrollmentId,
                classGroupId,
                date,
                status);
    }

    private JsonNode report(UUID actor, String role) throws Exception {
        String body =
                mvc.perform(
                                get(PATH)
                                        .header("Authorization", "Bearer " + tokenFor(actor, role))
                                        .param("id_class_group", language.toString())
                                        .param("trimester", "1")
                                        .param("limit", "200"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body);
    }

    private static JsonNode rowOf(JsonNode report, String studentName) {
        for (JsonNode row : report.get("students").get("content")) {
            if (row.get("studentName").asText().equals(studentName)) {
                return row;
            }
        }
        throw new AssertionError("No row for " + studentName);
    }

    @Test
    void countsOnlyThatClassGroupsSessions() throws Exception {
        LocalDate monday = LocalDate.of(2026, 3, 2);
        seedMark(anaEnrollment, language, monday, "Present");
        seedMark(anaEnrollment, language, LocalDate.of(2026, 3, 3), "Present");
        seedMark(anaEnrollment, language, LocalDate.of(2026, 3, 4), "Absent");
        seedMark(anaEnrollment, language, LocalDate.of(2026, 3, 5), "Late");
        // The daily mark and another subject's session, on a day already counted: neither counts.
        seedMark(anaEnrollment, null, monday, "Absent");
        seedMark(anaEnrollment, math, monday, "Absent");
        // Outside every configured period.
        seedMark(anaEnrollment, language, LocalDate.of(2026, 12, 20), "Absent");

        JsonNode ana = rowOf(report(technicalTeacher, "Teacher"), "Ana Aguilar");

        assertThat(ana.get("present").asLong()).isEqualTo(2);
        assertThat(ana.get("absent").asLong()).isEqualTo(1);
        assertThat(ana.get("late").asLong()).isEqualTo(1);
        assertThat(ana.get("computableSessions").asLong()).isEqualTo(4);
        assertThat(ana.get("percentage").asDouble()).isEqualTo(50.0);
    }

    /** Only the daily kind on record for Luis: in this subject nobody has marked him yet. */
    @Test
    void aStudentWithOnlyDailyMarksReadsAsUnmarkedInTheSubject() throws Exception {
        seedMark(luisEnrollment, null, LocalDate.of(2026, 3, 2), "Present");
        seedMark(luisEnrollment, math, LocalDate.of(2026, 3, 2), "Present");

        JsonNode luis = rowOf(report(technicalTeacher, "Teacher"), "Luis Zambrana");

        assertThat(luis.get("computableSessions").asLong()).isZero();
        assertThat(luis.get("percentage").isNull()).isTrue();
    }

    @Test
    void readsTheRollAlphabeticallyAndNamesItsScope() throws Exception {
        JsonNode node = report(director, "Director");

        List<String> names = new ArrayList<>();
        node.get("students").get("content").forEach(r -> names.add(r.get("studentName").asText()));
        assertThat(names).containsExactly("Ana Aguilar", "Luis Zambrana");
        assertThat(node.get("scope").asText()).isEqualTo("trimester");
        assertThat(node.get("trimester").asInt()).isEqualTo(1);
    }

    /** The homeroom teacher reads every class group of their course, as canReadClassGroup says. */
    @Test
    void homeroomTeacherReadsIt() throws Exception {
        assertThat(report(homeroomTeacher, "Teacher").get("students").get("content")).hasSize(2);
    }

    @Test
    void teacherWithNothingInTheClassGroup_isForbidden() throws Exception {
        mvc.perform(
                        get(PATH)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(unrelatedTeacher, "Teacher"))
                                .param("id_class_group", language.toString())
                                .param("trimester", "1"))
                .andExpect(status().isForbidden());
    }

    /** Teaching another subject of the same course does not open this one. */
    @Test
    void teacherOfAnotherSubjectOfTheCourse_isForbidden() throws Exception {
        mvc.perform(
                        get(PATH)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(mathTeacher, "Teacher"))
                                .param("id_class_group", language.toString())
                                .param("trimester", "1"))
                .andExpect(status().isForbidden());
    }
}
