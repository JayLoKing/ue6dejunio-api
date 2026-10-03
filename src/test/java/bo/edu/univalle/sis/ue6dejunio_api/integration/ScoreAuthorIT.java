package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every score records who wrote it (RF 45, RNF 5). Reassigning a homeroom moves its class groups to
 * the new teacher, so two teachers legitimately write the same class group within a year: the score
 * must keep the author of the first write and name whoever touched it last.
 */
class ScoreAuthorIT extends AbstractIntegrationTest {

    private static final Path V25 = Path.of("docs/sql/V25__score_author.sql");

    @Autowired private MockMvc mvc;

    private UUID teacherA;
    private UUID teacherB;
    private UUID classGroup;
    private UUID enrollment;
    private UUID eventOne;
    private UUID eventTwo;

    @BeforeEach
    void seed() {
        teacherA = seedUser("Teacher", false);
        teacherB = seedUser("Teacher", false);
        UUID course = seedCourse(teacherA, "A");
        classGroup = seedClassGroup(course, teacherA, "Matematicas");
        enrollment = seedEnrollment(seedStudent(), course);
        UUID criterion = seedActivityCriterion(classGroup, 1, "Knowing", "Prueba 1", "Escrita");
        eventOne = seedEvent(criterion, "Tema 1");
        eventTwo = seedEvent(criterion, "Tema 2");
    }

    private void putScore(UUID teacher, UUID eventId, String score) throws Exception {
        mvc.perform(
                        post("/api/assessment-scores")
                                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                new Body(enrollment, eventId, score))))
                .andExpect(status().isOk());
    }

    private Map<String, Object> assessmentRow(UUID eventId) {
        return jdbc.queryForMap(
                "SELECT id_assessment_score, created_by, updated_by FROM assessment_scores "
                        + "WHERE id_assessment_event = ?",
                eventId);
    }

    private Map<String, Object> academicRow() {
        return jdbc.queryForMap(
                "SELECT created_by, updated_by FROM academic_scores WHERE id_course_enrollment = ?",
                enrollment);
    }

    private String fullName(UUID userId) {
        return jdbc.queryForObject(
                "SELECT names || ' ' || last_names FROM users WHERE id_user = ?",
                String.class,
                userId);
    }

    private void reassignClassGroupToB() {
        jdbc.update(
                "UPDATE class_groups SET id_teacher = ? WHERE id_class_group = ?",
                teacherB,
                classGroup);
    }

    @Test
    void firstWrite_recordsTheTeacherAsCreatorAndLastEditor() throws Exception {
        putScore(teacherA, eventOne, "40");

        Map<String, Object> score = assessmentRow(eventOne);
        assertThat(score.get("created_by")).isEqualTo(teacherA);
        assertThat(score.get("updated_by")).isEqualTo(teacherA);
        Map<String, Object> academic = academicRow();
        assertThat(academic.get("created_by")).isEqualTo(teacherA);
        assertThat(academic.get("updated_by")).isEqualTo(teacherA);
    }

    @Test
    void anotherTeachersEdit_keepsTheCreatorAndMovesTheLastEditor() throws Exception {
        putScore(teacherA, eventOne, "40");
        reassignClassGroupToB();

        putScore(teacherB, eventOne, "35");

        Map<String, Object> score = assessmentRow(eventOne);
        assertThat(score.get("created_by")).isEqualTo(teacherA);
        assertThat(score.get("updated_by")).isEqualTo(teacherB);
        Map<String, Object> academic = academicRow();
        assertThat(academic.get("created_by")).isEqualTo(teacherA);
        assertThat(academic.get("updated_by")).isEqualTo(teacherB);
    }

    @Test
    void deleteByAnotherTeacher_leavesTheConsolidationRowNamingTheDeleter() throws Exception {
        putScore(teacherA, eventOne, "40");
        putScore(teacherA, eventTwo, "30");
        reassignClassGroupToB();
        UUID doomed = (UUID) assessmentRow(eventOne).get("id_assessment_score");

        mvc.perform(
                        delete("/api/assessment-scores/{id}", doomed)
                                .header("Authorization", "Bearer " + tokenFor(teacherB, "Teacher")))
                .andExpect(status().isNoContent());

        assertThat(academicRow().get("created_by")).isEqualTo(teacherA);
        assertThat(academicRow().get("updated_by")).isEqualTo(teacherB);
        // The score that survived was not touched, so it still names its own author.
        assertThat(assessmentRow(eventTwo).get("updated_by")).isEqualTo(teacherA);
    }

    @Test
    void readResponse_exposesWhoCreatedAndWhoLastEditedEachScore() throws Exception {
        putScore(teacherA, eventOne, "40");
        reassignClassGroupToB();
        putScore(teacherB, eventOne, "35");

        mvc.perform(
                        get("/api/assessment-scores/event/{eventId}", eventOne)
                                .header("Authorization", "Bearer " + tokenFor(teacherB, "Teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].createdBy.id").value(teacherA.toString()))
                .andExpect(jsonPath("$[0].createdBy.fullName").value(fullName(teacherA)))
                .andExpect(jsonPath("$[0].updatedBy.id").value(teacherB.toString()))
                .andExpect(jsonPath("$[0].updatedBy.fullName").value(fullName(teacherB)))
                .andExpect(jsonPath("$[0].score").value(35.0));
    }

    @Test
    void readResponse_ofARowWithNoRecordedAuthor_hasNullAuthors() throws Exception {
        seedEventScore(enrollment, eventOne, 40);

        mvc.perform(
                        get("/api/assessment-scores/event/{eventId}", eventOne)
                                .header("Authorization", "Bearer " + tokenFor(teacherA, "Teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$[0].updatedBy").doesNotExist());
    }

    /** V25 itself against the IT Postgres: it adds the three columns, a second run is a no-op. */
    @Test
    void v25Script_addsTheAuthorColumns_andIsIdempotent() throws Exception {
        String script = Files.readString(V25);
        try {
            jdbc.execute(
                    "ALTER TABLE assessment_scores DROP COLUMN created_by, DROP COLUMN updated_by");
            jdbc.execute("ALTER TABLE academic_scores DROP COLUMN updated_by");
            assertThat(authorColumns()).isEmpty();

            jdbc.execute(script);
            assertThat(authorColumns())
                    .containsExactlyInAnyOrder(
                            "assessment_scores.created_by",
                            "assessment_scores.updated_by",
                            "academic_scores.updated_by");

            jdbc.execute(script);
            assertThat(authorColumns()).hasSize(3);
        } finally {
            jdbc.execute(script);
        }
    }

    private List<String> authorColumns() {
        return jdbc.queryForList(
                "SELECT table_name || '.' || column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND ("
                        + "(table_name = 'assessment_scores' AND column_name IN ('created_by','updated_by')) "
                        + "OR (table_name = 'academic_scores' AND column_name = 'updated_by'))",
                String.class);
    }

    private record Body(
            @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
            @JsonProperty("id_assessment_event") UUID eventId,
            String score) {}
}
