package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec: only one teacher can be homeroom of a course at a time. PUT
 * /api/courses/{id}/homeroom-teacher must refuse a reassignment while the outgoing homeroom
 * teacher's account is still active, and must succeed once the Director deactivates that account in
 * Usuarios — proven over a real Postgres so the {@code UserEntity} to-one join backing {@code
 * Course#homeroomTeacherActive} is exercised for real, not mocked.
 */
class CourseHomeroomReassignmentIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID outgoingTeacher;
    private UUID incomingTeacher;
    private UUID courseId;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        outgoingTeacher = seedUser("Teacher", false);
        incomingTeacher = seedUser("Teacher", false);
        courseId = seedCourse(outgoingTeacher, "A");
    }

    private String putHomeroomTeacherBody(UUID teacherId) throws Exception {
        return json.writeValueAsString(Map.of("id_homeroom_teacher", teacherId.toString()));
    }

    @Test
    void outgoingTeacherStillActive_reassignmentRefused() throws Exception {
        mvc.perform(
                        put("/api/courses/{id}/homeroom-teacher", courseId)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(putHomeroomTeacherBody(incomingTeacher)))
                .andExpect(status().isConflict());
    }

    @Test
    void outgoingTeacherDeactivated_reassignmentSucceeds() throws Exception {
        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", outgoingTeacher);

        String body =
                mvc.perform(
                                put("/api/courses/{id}/homeroom-teacher", courseId)
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(putHomeroomTeacherBody(incomingTeacher)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode node = json.readTree(body);
        assertThat(node.get("homeroomTeacherId").asText()).isEqualTo(incomingTeacher.toString());
        assertThat(node.get("homeroomTeacherActive").asBoolean()).isTrue();
    }

    /**
     * The rule the Director asked for, measured over a real Postgres: "todas las que dictaba el
     * anterior docente". Every ACTIVE class group of {@code courseId} the outgoing teacher held
     * moves to the incoming teacher, technical subjects included. A class group of the same course
     * held by a third teacher is untouched, a class group the outgoing teacher holds in ANOTHER
     * course is untouched, and an INACTIVE class group of the outgoing teacher in this course is
     * untouched — it was closed, and rewriting its teacher would rewrite who taught it.
     */
    @Test
    void outgoingTeacherDeactivated_movesOnlyOutgoingTeachersActiveClassGroupsOfThatCourse()
            throws Exception {
        UUID thirdTeacher = seedUser("Teacher", false);
        UUID outgoingMatematicas = seedClassGroup(courseId, outgoingTeacher, "Matematicas");
        UUID thirdTeacherLenguaje = seedClassGroup(courseId, thirdTeacher, "Lenguaje");

        UUID otherCourseId = seedCourse(director, "B");
        UUID outgoingInOtherCourse = seedClassGroup(otherCourseId, outgoingTeacher, "Matematicas");

        UUID historiaSubjectId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO subjects (id_subject, name, id_area) "
                        + "SELECT ?, 'Historia-IT', id_area FROM knowledge_areas LIMIT 1",
                historiaSubjectId);
        UUID outgoingInactiveInCourse = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO class_groups (id_class_group, id_course, id_subject, id_teacher, "
                        + "is_active) VALUES (?,?,?,?,false)",
                outgoingInactiveInCourse,
                courseId,
                historiaSubjectId,
                outgoingTeacher);

        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", outgoingTeacher);

        mvc.perform(
                        put("/api/courses/{id}/homeroom-teacher", courseId)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(putHomeroomTeacherBody(incomingTeacher)))
                .andExpect(status().isOk());

        assertThat(teacherOfClassGroup(outgoingMatematicas)).isEqualTo(incomingTeacher);
        assertThat(teacherOfClassGroup(thirdTeacherLenguaje)).isEqualTo(thirdTeacher);
        assertThat(teacherOfClassGroup(outgoingInOtherCourse)).isEqualTo(outgoingTeacher);
        assertThat(teacherOfClassGroup(outgoingInactiveInCourse)).isEqualTo(outgoingTeacher);
    }

    private UUID teacherOfClassGroup(UUID classGroupId) {
        return jdbc.queryForObject(
                "SELECT id_teacher FROM class_groups WHERE id_class_group = ?",
                UUID.class,
                classGroupId);
    }
}
