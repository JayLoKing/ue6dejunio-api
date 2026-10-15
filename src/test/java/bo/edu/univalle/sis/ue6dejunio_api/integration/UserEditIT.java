package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** PUT /api/users/{id}: what the Director's edit form can and cannot change. */
class UserEditIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private IEmailService emailService;

    private UUID director;
    private UUID teacher;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
    }

    private ResultActions edit(UUID id, String body) throws Exception {
        return mvc.perform(
                put("/api/users/{id}", id)
                        .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private boolean isActive(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT is_active FROM users WHERE id_user = ?", Boolean.class, id));
    }

    // Closing an account has its own endpoints, with their own checks and their own audit trail.
    // An edit that also carried the flag was a second way to do it that skipped all of that.
    @Test
    void anEditCarryingActiveFalse_leavesTheAccountOpen() throws Exception {
        edit(teacher, "{\"names\":\"Renombrado\",\"active\":false}").andExpect(status().isOk());

        assertThat(isActive(teacher)).isTrue();
    }

    private String phoneOf(UUID id) {
        return jdbc.queryForObject("SELECT phone FROM users WHERE id_user = ?", String.class, id);
    }

    @Test
    void anotherUsersPhone_isAConflictAndChangesNothing() throws Exception {
        UUID secretary = seedUser("Secretary", false);
        String before = phoneOf(teacher);

        edit(teacher, "{\"phone\":\"" + phoneOf(secretary) + "\"}")
                .andExpect(status().isConflict());

        assertThat(phoneOf(teacher)).isEqualTo(before);
    }

    // The form posts every field, so the number this user already has comes back on every save.
    @Test
    void theUsersOwnPhone_isNotAConflict() throws Exception {
        String own = phoneOf(teacher);

        edit(teacher, "{\"names\":\"Renombrado\",\"phone\":\"" + own + "\"}")
                .andExpect(status().isOk());

        assertThat(phoneOf(teacher)).isEqualTo(own);
    }

    @Test
    void anEditCarryingActiveTrue_doesNotReopenAClosedAccount() throws Exception {
        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", teacher);

        edit(teacher, "{\"names\":\"Renombrado\",\"active\":true}").andExpect(status().isOk());

        assertThat(isActive(teacher)).isFalse();
    }
}
