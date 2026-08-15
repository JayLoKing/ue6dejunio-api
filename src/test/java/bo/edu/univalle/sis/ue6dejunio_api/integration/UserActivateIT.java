package bo.edu.univalle.sis.ue6dejunio_api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec: RF11 explicit user activation — POST /api/users/{id}/activate (Director only). */
class UserActivateIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", teacher);
    }

    @Test
    void activate_deactivatedUser_returns200AndActiveTrue() throws Exception {
        mvc.perform(post("/api/users/{id}/activate", teacher)
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void activate_alreadyActiveUser_isIdempotent_returns200() throws Exception {
        jdbc.update("UPDATE users SET is_active = true WHERE id_user = ?", teacher);

        mvc.perform(post("/api/users/{id}/activate", teacher)
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void activate_nonexistentUser_returns404() throws Exception {
        mvc.perform(post("/api/users/{id}/activate", UUID.randomUUID())
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isNotFound());
    }

    @Test
    void activate_nonDirectorRole_returns403() throws Exception {
        mvc.perform(post("/api/users/{id}/activate", teacher)
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isForbidden());
    }
}
