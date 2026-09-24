package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec: Score read ownership guard — ScoreController.byCourseEnrollment (course-wide consolidated,
 * homeroom-teacher-only per domain rules).
 */
class ScoreAuthorizationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID homeroomTeacher;
    private UUID otherTeacher;
    private UUID director;
    private UUID enrollmentId;

    @BeforeEach
    void seed() {
        homeroomTeacher = seedUser("Teacher", false);
        otherTeacher = seedUser("Teacher", false);
        director = seedUser("Director", false);
        UUID course = seedCourse(homeroomTeacher, "A");
        UUID student = seedStudent();
        enrollmentId = seedEnrollment(student, course);
    }

    @Test
    void nonOwnerTeacher_byCourseEnrollment_forbidden() throws Exception {
        mvc.perform(
                        get("/api/scores")
                                .param("id_course_enrollment", enrollmentId.toString())
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(otherTeacher, "Teacher")))
                .andExpect(status().isForbidden());
    }

    @Test
    void homeroomOwnerAndDirector_byCourseEnrollment_ok() throws Exception {
        mvc.perform(
                        get("/api/scores")
                                .param("id_course_enrollment", enrollmentId.toString())
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher")))
                .andExpect(status().isOk());

        mvc.perform(
                        get("/api/scores")
                                .param("id_course_enrollment", enrollmentId.toString())
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isOk());
    }
}
