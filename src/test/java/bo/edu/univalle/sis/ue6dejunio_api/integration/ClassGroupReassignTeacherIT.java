package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec: RF12 reassign class group teacher — PUT
 * /api/courses/{courseId}/class-groups/{classGroupId}/teacher.
 */
class ClassGroupReassignTeacherIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID technicalTeacher;
    private UUID aulaTeacher;
    private UUID courseA;
    private UUID courseB;
    private UUID classGroupTechnical;
    private UUID classGroupNonTechnical;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        technicalTeacher = seedUser("Teacher", true);
        aulaTeacher = seedUser("Teacher", false);
        courseA = seedCourse(aulaTeacher, "A");
        courseB = seedCourse(aulaTeacher, "B");

        UUID technicalSubjectId = UUID.randomUUID();
        // Every subject belongs to a knowledge area since the curriculum plan groups them by it,
        // so a fixture subject has to name one too.
        jdbc.update(
                "INSERT INTO subjects (id_subject, name, id_area, is_technical) "
                        + "VALUES (?,?,(SELECT id_area FROM knowledge_areas "
                        + "WHERE name = 'Ciencia Tecnología y Producción'),true)",
                technicalSubjectId,
                "Robotica-" + technicalSubjectId.toString().substring(0, 8));

        classGroupTechnical = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO class_groups (id_class_group, id_course, id_subject, id_teacher, is_active) "
                        + "VALUES (?,?,?,?,true)",
                classGroupTechnical,
                courseA,
                technicalSubjectId,
                technicalTeacher);

        classGroupNonTechnical = seedClassGroup(courseA, aulaTeacher, "Matematicas");
    }

    @Test
    void reassign_technicalTeacherToTechnicalClassGroup_returns200() throws Exception {
        UUID otherTechnicalTeacher = seedUser("Teacher", true);
        mvc.perform(
                        put(
                                        "/api/courses/{c}/class-groups/{g}/teacher",
                                        courseA,
                                        classGroupTechnical)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("teacherId", otherTechnicalTeacher))))
                .andExpect(status().isOk());
    }

    @Test
    void reassign_technicalClassGroupToTheCoursesOwnHomeroomTeacher_returns200() throws Exception {
        // aulaTeacher runs courseA. There are not enough technical teachers to cover every course,
        // so the school has the teacher in charge take its technical subjects too.
        mvc.perform(
                        put(
                                        "/api/courses/{c}/class-groups/{g}/teacher",
                                        courseA,
                                        classGroupTechnical)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("teacherId", aulaTeacher))))
                .andExpect(status().isOk());
    }

    /** The licence is over their own course. An aula teacher from elsewhere is still refused. */
    @Test
    void reassign_technicalClassGroupToAnOutsideAulaTeacher_returns409() throws Exception {
        UUID outsider = seedUser("Teacher", false);

        mvc.perform(
                        put(
                                        "/api/courses/{c}/class-groups/{g}/teacher",
                                        courseA,
                                        classGroupTechnical)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("teacherId", outsider))))
                .andExpect(status().isConflict());
    }

    @Test
    void reassign_technicalTeacherToNonTechnicalClassGroup_returns409() throws Exception {
        mvc.perform(
                        put(
                                        "/api/courses/{c}/class-groups/{g}/teacher",
                                        courseA,
                                        classGroupNonTechnical)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("teacherId", technicalTeacher))))
                .andExpect(status().isConflict());
    }

    @Test
    void reassign_classGroupBelongsToDifferentCourse_returns404_antiIdor() throws Exception {
        mvc.perform(
                        put(
                                        "/api/courses/{c}/class-groups/{g}/teacher",
                                        courseB,
                                        classGroupTechnical)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("teacherId", technicalTeacher))))
                .andExpect(status().isNotFound());
    }

    @Test
    void reassign_nonexistentTeacher_returns404() throws Exception {
        mvc.perform(
                        put(
                                        "/api/courses/{c}/class-groups/{g}/teacher",
                                        courseA,
                                        classGroupTechnical)
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("teacherId", UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void reassign_nonexistentClassGroup_returns404() throws Exception {
        mvc.perform(
                        put("/api/courses/{c}/class-groups/{g}/teacher", courseA, UUID.randomUUID())
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("teacherId", technicalTeacher))))
                .andExpect(status().isNotFound());
    }

    @Test
    void reassign_nonDirectorRole_returns403() throws Exception {
        mvc.perform(
                        put(
                                        "/api/courses/{c}/class-groups/{g}/teacher",
                                        courseA,
                                        classGroupTechnical)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(technicalTeacher, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("teacherId", technicalTeacher))))
                .andExpect(status().isForbidden());
    }
}
