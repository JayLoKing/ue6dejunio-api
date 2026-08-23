package bo.edu.univalle.sis.ue6dejunio_api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec: gradebook-read-authorization (Modified Capability) — retrofits
 * {@code @authz.canReadCourse} onto the previously-unguarded {@code GradebookController}
 * centralizer and attendance read endpoints. Behavior change: these reads were previously open
 * to any authenticated teacher (including technical teachers with no relation to the course).
 */
class GradebookReadAuthorizationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID homeroomTeacher;
    private UUID otherTeacher;
    private UUID courseA;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        homeroomTeacher = seedUser("Teacher", false);
        otherTeacher = seedUser("Teacher", false);
        courseA = seedCourse(homeroomTeacher, "A");
        seedClassGroup(courseA, homeroomTeacher, "Matematicas");
    }

    // ---- student-summary: it was the one read in this controller with no ownership guard ----

    @Test
    void studentSummary_nonOwnerTeacher_forbidden() throws Exception {
        UUID enrollmentInA = seedEnrollment(seedStudent(), courseA);

        // Without an ownership check any authenticated teacher could read another course's grades
        // just by guessing an enrollment id.
        mvc.perform(get("/api/gradebook/student-summary")
                .header("Authorization", "Bearer " + tokenFor(otherTeacher, "Teacher"))
                .param("id_course_enrollment", enrollmentInA.toString())
                .param("trimester", "1"))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentSummary_homeroomTeacher_ok() throws Exception {
        UUID enrollmentInA = seedEnrollment(seedStudent(), courseA);

        mvc.perform(get("/api/gradebook/student-summary")
                .header("Authorization", "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                .param("id_course_enrollment", enrollmentInA.toString())
                .param("trimester", "1"))
            .andExpect(status().isOk());
    }

    @Test
    void centralizer_director_ok() throws Exception {
        mvc.perform(get("/api/gradebook/centralizer")
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("id_course", courseA.toString())
                .param("trimester", "1"))
            .andExpect(status().isOk());
    }

    @Test
    void centralizer_homeroomTeacher_ok() throws Exception {
        mvc.perform(get("/api/gradebook/centralizer")
                .header("Authorization", "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                .param("id_course", courseA.toString())
                .param("trimester", "1"))
            .andExpect(status().isOk());
    }

    @Test
    void centralizer_nonOwnerTeacher_forbidden() throws Exception {
        mvc.perform(get("/api/gradebook/centralizer")
                .header("Authorization", "Bearer " + tokenFor(otherTeacher, "Teacher"))
                .param("id_course", courseA.toString())
                .param("trimester", "1"))
            .andExpect(status().isForbidden());
    }

    @Test
    void centralizer_unauthenticated_401() throws Exception {
        mvc.perform(get("/api/gradebook/centralizer")
                .param("id_course", courseA.toString())
                .param("trimester", "1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void attendance_director_ok() throws Exception {
        mvc.perform(get("/api/gradebook/attendance")
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("id_course", courseA.toString()))
            .andExpect(status().isOk());
    }

    @Test
    void attendance_homeroomTeacher_ok() throws Exception {
        mvc.perform(get("/api/gradebook/attendance")
                .header("Authorization", "Bearer " + tokenFor(homeroomTeacher, "Teacher"))
                .param("id_course", courseA.toString()))
            .andExpect(status().isOk());
    }

    @Test
    void attendance_nonOwnerTeacher_forbidden() throws Exception {
        mvc.perform(get("/api/gradebook/attendance")
                .header("Authorization", "Bearer " + tokenFor(otherTeacher, "Teacher"))
                .param("id_course", courseA.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void attendance_unauthenticated_401() throws Exception {
        mvc.perform(get("/api/gradebook/attendance")
                .param("id_course", courseA.toString()))
            .andExpect(status().isUnauthorized());
    }
}
