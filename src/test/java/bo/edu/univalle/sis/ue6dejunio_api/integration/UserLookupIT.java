package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/users/lookup?email= — what the Director's handover form asks before it posts. */
class UserLookupIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private String teacherEmail;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        teacherEmail =
                jdbc.queryForObject(
                        "SELECT email FROM users WHERE id_user = ?", String.class, teacher);
    }

    private String bearer(UUID id, String role) {
        return "Bearer " + tokenFor(id, role);
    }

    @Test
    void director_findsAnExistingUserByEmail() throws Exception {
        mvc.perform(
                        get("/api/users/lookup")
                                .param("email", teacherEmail)
                                .header("Authorization", bearer(director, "Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(teacher.toString()))
                .andExpect(jsonPath("$.email").value(teacherEmail))
                .andExpect(jsonPath("$.role").value("Teacher"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.fullName").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void director_unknownEmail_getsNoContent() throws Exception {
        mvc.perform(
                        get("/api/users/lookup")
                                .param("email", "nadie@ue6.test")
                                .header("Authorization", bearer(director, "Director")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void director_malformedEmail_is400() throws Exception {
        mvc.perform(
                        get("/api/users/lookup")
                                .param("email", "no-es-un-correo")
                                .header("Authorization", bearer(director, "Director")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacherAndSecretary_get403() throws Exception {
        UUID secretary = seedUser("Secretary", false);

        mvc.perform(
                        get("/api/users/lookup")
                                .param("email", teacherEmail)
                                .header("Authorization", bearer(teacher, "Teacher")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/users/lookup")
                                .param("email", teacherEmail)
                                .header("Authorization", bearer(secretary, "Secretary")))
                .andExpect(status().isForbidden());
    }
}
