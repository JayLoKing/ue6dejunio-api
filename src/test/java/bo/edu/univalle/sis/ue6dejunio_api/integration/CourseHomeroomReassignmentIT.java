package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
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

    private String swapBody(UUID courseAId, UUID courseBId) throws Exception {
        return json.writeValueAsString(
                Map.of(
                        "id_course_a", courseAId.toString(),
                        "id_course_b", courseBId.toString()));
    }

    private UUID insertSubject(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO subjects (id_subject, name, id_area) "
                        + "SELECT ?, ?, id_area FROM knowledge_areas LIMIT 1",
                id,
                name);
        return id;
    }

    private UUID seedClassGroupBySubjectId(UUID ofCourseId, UUID teacherId, UUID subjectId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO class_groups (id_class_group, id_course, id_subject, id_teacher, "
                        + "is_active) VALUES (?,?,?,?,true)",
                id,
                ofCourseId,
                subjectId,
                teacherId);
        return id;
    }

    /**
     * Spec: {@code PUT /api/courses/homeroom-teachers/swap} trades the homeroom teacher of two
     * courses, both active, and each teacher inherits what the outgoing teacher held IN THE COURSE
     * THEY ARRIVE AT — never what they used to teach themselves. The user's own example: 1ro A's
     * homeroom teaches 7 of its 9 subjects (a third teacher holds the other 2); 2do A's homeroom
     * teaches all 9. After the swap, the one arriving at 1ro A teaches exactly those 7, and the one
     * arriving at 2do A teaches all 9 — and the third teacher's 2 subjects in 1ro A never moved.
     */
    @Test
    void swap_bothActive_eachTeacherInheritsTheArrivingCoursesSubjects() throws Exception {
        UUID director2 = seedUser("Director", false);
        UUID teacherA = seedUser("Teacher", false);
        UUID teacherB = seedUser("Teacher", false);
        UUID thirdTeacher = seedUser("Teacher", false);

        // "B" and "C": the @BeforeEach above already seeded a course on parallel "A" for this
        // same grade and gestión, and (id_grade, id_parallel, id_academic_year) is UNIQUE.
        UUID courseA = seedCourse(teacherA, "B");
        UUID courseB = seedCourse(teacherB, "C");

        // 1ro A: 9 subjects, 7 held by teacherA, 2 held by a third teacher.
        List<UUID> aTeacherGroups = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            UUID subjectId = insertSubject("SwapIT-A-" + i);
            aTeacherGroups.add(seedClassGroupBySubjectId(courseA, teacherA, subjectId));
        }
        List<UUID> thirdTeacherGroups = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            UUID subjectId = insertSubject("SwapIT-A-third-" + i);
            thirdTeacherGroups.add(seedClassGroupBySubjectId(courseA, thirdTeacher, subjectId));
        }

        // 2do A (course B here): 9 subjects, all held by teacherB.
        List<UUID> bTeacherGroups = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            UUID subjectId = insertSubject("SwapIT-B-" + i);
            bTeacherGroups.add(seedClassGroupBySubjectId(courseB, teacherB, subjectId));
        }

        mvc.perform(
                        put("/api/courses/homeroom-teachers/swap")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director2, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(swapBody(courseA, courseB)))
                .andExpect(status().isOk());

        // Homeroom teachers traded places.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT id_homeroom_teacher FROM courses WHERE id_course = ?",
                                UUID.class,
                                courseA))
                .isEqualTo(teacherB);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT id_homeroom_teacher FROM courses WHERE id_course = ?",
                                UUID.class,
                                courseB))
                .isEqualTo(teacherA);

        // teacherB (arriving at course A) now teaches exactly the 7 subjects teacherA held there.
        for (UUID classGroupId : aTeacherGroups) {
            assertThat(teacherOfClassGroup(classGroupId)).isEqualTo(teacherB);
        }
        // The third teacher's 2 subjects in course A never moved.
        for (UUID classGroupId : thirdTeacherGroups) {
            assertThat(teacherOfClassGroup(classGroupId)).isEqualTo(thirdTeacher);
        }
        // teacherA (arriving at course B) now teaches all 9 subjects teacherB held there.
        for (UUID classGroupId : bTeacherGroups) {
            assertThat(teacherOfClassGroup(classGroupId)).isEqualTo(teacherA);
        }
    }

    @Test
    void swap_sameCourseTwice_refused() throws Exception {
        UUID director2 = seedUser("Director", false);
        mvc.perform(
                        put("/api/courses/homeroom-teachers/swap")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director2, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(swapBody(courseId, courseId)))
                .andExpect(status().isConflict());
    }

    @Test
    void swap_oneCourseHasNoHomeroomTeacher_refused() throws Exception {
        UUID director2 = seedUser("Director", false);
        UUID courseWithNoHomeroom = seedCourse(null, "B");

        mvc.perform(
                        put("/api/courses/homeroom-teachers/swap")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director2, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(swapBody(courseId, courseWithNoHomeroom)))
                .andExpect(status().isConflict());
    }

    @Test
    void swap_oneTeacherInactive_refused() throws Exception {
        UUID director2 = seedUser("Director", false);
        UUID courseB = seedCourse(incomingTeacher, "B");
        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", incomingTeacher);

        mvc.perform(
                        put("/api/courses/homeroom-teachers/swap")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director2, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(swapBody(courseId, courseB)))
                .andExpect(status().isConflict());
    }
}
