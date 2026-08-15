package bo.edu.univalle.sis.ue6dejunio_api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec: Teacher roster self-or-Director guard — TeacherController class-groups/students. */
class TeacherRosterAuthorizationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID teacherA;
    private UUID teacherB;
    private UUID director;

    @BeforeEach
    void seed() {
        teacherA = seedUser("Teacher", false);
        teacherB = seedUser("Teacher", false);
        director = seedUser("Director", false);
    }

    @Test
    void foreignTeacher_classGroups_forbidden() throws Exception {
        mvc.perform(get("/api/teachers/{userId}/class-groups", teacherA)
                .header("Authorization", "Bearer " + tokenFor(teacherB, "Teacher")))
            .andExpect(status().isForbidden());
    }

    @Test
    void foreignTeacher_students_forbidden() throws Exception {
        mvc.perform(get("/api/teachers/{userId}/students", teacherA)
                .header("Authorization", "Bearer " + tokenFor(teacherB, "Teacher")))
            .andExpect(status().isForbidden());
    }

    @Test
    void self_classGroupsAndStudents_ok() throws Exception {
        mvc.perform(get("/api/teachers/{userId}/class-groups", teacherA)
                .header("Authorization", "Bearer " + tokenFor(teacherA, "Teacher")))
            .andExpect(status().isOk());

        mvc.perform(get("/api/teachers/{userId}/students", teacherA)
                .header("Authorization", "Bearer " + tokenFor(teacherA, "Teacher")))
            .andExpect(status().isOk());
    }

    @Test
    void director_anyTeacherRoster_ok() throws Exception {
        mvc.perform(get("/api/teachers/{userId}/class-groups", teacherA)
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isOk());
    }
}
