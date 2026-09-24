package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The informe pedagógico end to end, over a real Postgres.
 *
 * <p>The derivation is unit-tested against mocked ports that hand back the totals the test itself
 * wrote. What only a database can answer is whether section III and section IV are built from the
 * same {@code total_score} the school's other sheets print — a generated column this code never
 * writes — whether the effective roster really excludes a withdrawn student, and whether the write
 * side is reachable by the teacher who signs the document and by nobody else.
 */
class PedagogicalReportIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID homeroomTeacher;
    private UUID anotherTeacher;
    private UUID courseId;
    private UUID mathGroup;
    private UUID anaEnrollment;
    private UUID brunoEnrollment;
    private UUID carlaEnrollment;

    @BeforeEach
    void seedClassroom() {
        director = seedUser("Director", false);
        homeroomTeacher = seedUser("Teacher", false);
        anotherTeacher = seedUser("Teacher", false);

        courseId = seedCourse(homeroomTeacher, "A");
        mathGroup = seedClassGroup(courseId, homeroomTeacher, "Matematicas");
        UUID languageGroup = seedClassGroup(courseId, homeroomTeacher, "Lenguaje");

        // Ana passes both, Bruno fails both, Carla fails one of the two. Marks nowhere near 51, so
        // nothing here turns on rounding.
        UUID ana = seedStudent("Ana", "Alvarez");
        UUID bruno = seedStudent("Bruno", "Bermudez");
        UUID carla = seedStudent("Carla", "Caceres");
        setGender(ana, "F");
        setGender(bruno, "M");
        setGender(carla, "F");
        anaEnrollment = seedEnrollment(ana, courseId);
        brunoEnrollment = seedEnrollment(bruno, courseId);
        carlaEnrollment = seedEnrollment(carla, courseId);

        gradeArea(anaEnrollment, mathGroup, 10, 40, 35, 5); // 90
        gradeArea(anaEnrollment, languageGroup, 10, 40, 30, 5); // 85
        gradeArea(brunoEnrollment, mathGroup, 2, 10, 10, 1); // 23
        gradeArea(brunoEnrollment, languageGroup, 3, 12, 12, 1); // 28
        gradeArea(carlaEnrollment, mathGroup, 10, 40, 35, 5); // 90
        gradeArea(carlaEnrollment, languageGroup, 2, 15, 12, 1); // 30
    }

    private void setGender(UUID studentId, String gender) {
        jdbc.update("UPDATE students SET gender = ? WHERE id_student = ?", gender, studentId);
    }

    /** One area graded in the first trimester. {@code total_score} is the database's own sum. */
    private void gradeArea(
            UUID enrollmentId, UUID classGroupId, int being, int knowing, int doing, int deciding) {
        jdbc.update(
                "INSERT INTO academic_scores (id_academic_score, id_course_enrollment, id_class_group, "
                        + "trimester, score_being, score_knowing, score_doing, score_deciding) "
                        + "VALUES (?,?,?,1,?,?,?,?)",
                UUID.randomUUID(),
                enrollmentId,
                classGroupId,
                BigDecimal.valueOf(being),
                BigDecimal.valueOf(knowing),
                BigDecimal.valueOf(doing),
                BigDecimal.valueOf(deciding));
    }

    private String sheetAs(UUID userId, String role) throws Exception {
        return mvc.perform(
                        get("/api/gradebook/pedagogical-report")
                                .header("Authorization", "Bearer " + tokenFor(userId, role))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void sheet_countsTheRosterByGenderOffTheMarksTheOtherSheetsPrint() throws Exception {
        String body = sheetAs(director, "Director");

        assertThat(JsonPath.<Integer>read(body, "$.stats.effective.total")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(body, "$.stats.effective.male")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.stats.effective.female")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(body, "$.stats.passed.total")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(body, "$.stats.failed.total")).isEqualTo(2);
        assertThat(new BigDecimal(JsonPath.read(body, "$.stats.failed.percentage").toString()))
                .isEqualByComparingTo("66.67");
    }

    @Test
    void sheet_namesTheClassroomAndTheTeacherWhoSignsIt() throws Exception {
        String body = sheetAs(director, "Director");

        assertThat(JsonPath.<String>read(body, "$.parallelName")).isEqualTo("A");
        assertThat(JsonPath.<Integer>read(body, "$.trimester")).isEqualTo(1);
        assertThat(JsonPath.<String>read(body, "$.homeroomTeacherName")).isNotNull();
        assertThat(JsonPath.<Boolean>read(body, "$.exists")).isFalse();
    }

    @Test
    void sheet_listsOnlyTheAreasEachStudentActuallyFailed() throws Exception {
        String body = sheetAs(director, "Director");

        assertThat(JsonPath.<List<String>>read(body, "$.failingStudents[*].fullName"))
                .containsExactly("Bruno Bermudez", "Carla Caceres");
        assertThat(JsonPath.<List<Integer>>read(body, "$.failingStudents[*].number"))
                .containsExactly(1, 2);
        assertThat(
                        JsonPath.<List<String>>read(
                                body, "$.failingStudents[0].failedAreas[*].subjectName"))
                .containsExactly("Lenguaje", "Matematicas");
        assertThat(
                        JsonPath.<List<String>>read(
                                body, "$.failingStudents[1].failedAreas[*].subjectName"))
                .containsExactly("Lenguaje");
        assertThat(
                        new BigDecimal(
                                JsonPath.read(body, "$.failingStudents[1].failedAreas[0].mark")
                                        .toString()))
                .isEqualByComparingTo("30");
    }

    /**
     * EFECTIVOS is the sheet's own word, and a student who left in April is not one. This is where
     * the informe parts company with the libreta and the centralizador, which keep counting a
     * withdrawn student because they still own the marks they earned.
     */
    @Test
    void sheet_withdrawnStudent_isNotAmongTheEffectiveRoster() throws Exception {
        jdbc.update(
                "UPDATE course_enrollments SET status = 'Withdrawn' "
                        + "WHERE id_course_enrollment = ?",
                brunoEnrollment);

        String body = sheetAs(director, "Director");

        assertThat(JsonPath.<Integer>read(body, "$.stats.effective.total")).isEqualTo(2);
        assertThat(JsonPath.<List<String>>read(body, "$.failingStudents[*].fullName"))
                .containsExactly("Carla Caceres");
    }

    @Test
    void save_thenRead_carriesBackWhatTheTeacherWroteOnEachFailingStudent() throws Exception {
        String request =
                """
            {
              "achievements": "Ana Alvarez sobresale en las dos areas.",
              "difficulties": "Dificultades en operaciones basicas.",
              "failingStudents": [
                {"idCourseEnrollment": "%s", "actions": "Refuerzo en horario alterno",
                 "verificationSource": "Cuaderno de seguimiento"}
              ]
            }
            """
                        .formatted(brunoEnrollment);

        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(
                        jsonPath("$.achievements").value("Ana Alvarez sobresale en las dos areas."))
                .andExpect(
                        jsonPath("$.failingStudents[0].actions")
                                .value("Refuerzo en horario alterno"));

        String body = sheetAs(director, "Director");
        assertThat(JsonPath.<String>read(body, "$.difficulties"))
                .isEqualTo("Dificultades en operaciones basicas.");
        assertThat(JsonPath.<String>read(body, "$.failingStudents[0].verificationSource"))
                .isEqualTo("Cuaderno de seguimiento");
        // Carla is failing too and has nothing written about her yet.
        assertThat(JsonPath.<String>read(body, "$.failingStudents[1].actions")).isNull();
    }

    /**
     * A report half written has to be savable: the teacher fills the prose and comes back later.
     */
    @Test
    void save_withNothingButProse_isAccepted() throws Exception {
        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"achievements\":\"Solo los logros por ahora\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.difficulties").doesNotExist());
    }

    private void writeBrunosParagraph() throws Exception {
        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"failingStudents\":[{\"idCourseEnrollment\":\""
                                                + brunoEnrollment
                                                + "\",\"actions\":\"Refuerzo\",\"verificationSource\":\"Cuaderno\"}]}"))
                .andExpect(status().isOk());
    }

    /**
     * Saving the prose alone is the obvious partial update, and it must not take section IV with
     * it. Those paragraphs are prose a teacher typed; nothing here keeps a second copy of them, so
     * a body that omits the field has to mean "I am not talking about section IV".
     */
    @Test
    void save_omittingTheFailingStudentsField_leavesTheStoredParagraphsAlone() throws Exception {
        writeBrunosParagraph();

        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"achievements\":\"Solo cambio los logros\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failingStudents[0].actions").value("Refuerzo"));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM pedagogical_report_failures", Integer.class))
                .isEqualTo(1);
        String body = sheetAs(director, "Director");
        assertThat(JsonPath.<String>read(body, "$.achievements"))
                .isEqualTo("Solo cambio los logros");
        assertThat(JsonPath.<String>read(body, "$.failingStudents[0].verificationSource"))
                .isEqualTo("Cuaderno");
    }

    /** Sending the field empty is the other half of the rule: section IV now holds nothing. */
    @Test
    void save_withAnEmptyFailingStudentsList_clearsSectionFour() throws Exception {
        writeBrunosParagraph();

        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"failingStudents\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failingStudents[0].actions").doesNotExist());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM pedagogical_report_failures", Integer.class))
                .isZero();
    }

    /**
     * A literal null inside the array. {@code @Valid} cascades into the elements that exist, so the
     * {@code @NotNull} on the enrolment is never reached for this one — without a constraint on the
     * element itself, a parseable body would become a 500 where the sibling case, an object missing
     * its enrolment, correctly answers 400.
     */
    @Test
    void save_withANullElementInFailingStudents_isRejected() throws Exception {
        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"failingStudents\":[null]}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * The foreign key points at any enrolment in the school. A note about a child of another
     * classroom would never be printed — section IV is drawn from this course's roster — so it is
     * refused rather than stored where nobody will find it.
     */
    @Test
    void save_noteAboutAnotherClassroomsStudent_isRefused() throws Exception {
        UUID otherCourse = seedCourse(anotherTeacher, "B");
        UUID stranger = seedEnrollment(seedStudent("Dario", "Duran"), otherCourse);

        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"failingStudents\":[{\"idCourseEnrollment\":\""
                                                + stranger
                                                + "\"}]}"))
                .andExpect(status().isBadRequest());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM pedagogical_report_failures", Integer.class))
                .isZero();
    }

    /**
     * The document carries one DOCENTE and closes over their signature. Reading it is the office's
     * business; writing inside it is not.
     */
    @Test
    void save_byTheDirector_isForbidden() throws Exception {
        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"achievements\":\"Escrito desde direccion\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void save_byATeacherWhoIsNotTheHomeroomOfTheCourse_isForbidden() throws Exception {
        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(anotherTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"achievements\":\"Escrito por otro docente\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sheet_byATeacherWhoIsNotTheHomeroomOfTheCourse_isForbidden() throws Exception {
        mvc.perform(
                        get("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(anotherTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1"))
                .andExpect(status().isForbidden());
    }

    /**
     * Saving twice leaves one document: {@code uq_pedagogical_report} makes the second a
     * correction.
     */
    @Test
    void save_twice_leavesOneDocumentAndTheLaterText() throws Exception {
        for (String text : List.of("Primer borrador", "Version final")) {
            mvc.perform(
                            put("/api/gradebook/pedagogical-report")
                                    .header(
                                            "Authorization",
                                            "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                    .param("id_course", courseId.toString())
                                    .param("trimester", "1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"achievements\":\"" + text + "\"}"))
                    .andExpect(status().isOk());
        }

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM pedagogical_reports WHERE id_course = ?",
                                Integer.class,
                                courseId))
                .isEqualTo(1);
        assertThat(JsonPath.<String>read(sheetAs(director, "Director"), "$.achievements"))
                .isEqualTo("Version final");
    }

    /** Three trimesters, three documents. The second must not reach into the first. */
    @Test
    void save_onlyTouchesTheTrimesterItNames() throws Exception {
        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"achievements\":\"Logros del primero\"}"))
                .andExpect(status().isOk());

        mvc.perform(
                        get("/api/gradebook/pedagogical-report")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.achievements").doesNotExist());
    }

    /** Outside the three trimesters the school has, there is no document to ask for. */
    @Test
    void sheet_trimesterOutsideTheYear_isRejected() throws Exception {
        mvc.perform(
                        get("/api/gradebook/pedagogical-report")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "4"))
                .andExpect(status().isBadRequest());
    }

    /**
     * The columns behind section II are {@code text} on purpose, so the only ceiling on a teacher's
     * prose is the one this endpoint declares. Without it a single PUT accepts a body of any size.
     */
    @Test
    void save_proseLongerThanTheDeclaredCeiling_isRejected() throws Exception {
        String tooLong = "a".repeat(4001);

        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"achievements\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedagogical_reports", Integer.class))
                .isZero();
    }

    /** A note attached to nobody could never be shown again, so it is refused at the edge. */
    @Test
    void save_noteWithoutAnEnrolment_isRejected() throws Exception {
        mvc.perform(
                        put("/api/gradebook/pedagogical-report")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                                .param("id_course", courseId.toString())
                                .param("trimester", "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"failingStudents\":[{\"actions\":\"Sin matricula\"}]}"))
                .andExpect(status().isBadRequest());
    }
}
