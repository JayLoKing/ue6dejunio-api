package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** POST /api/users/{id}/resend-credentials, and the list column the web enables the button by. */
class ResendCredentialsIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private IEmailService emailService;

    private UUID director;
    private UUID pending;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        pending = seedUser("Teacher", false);
        jdbc.update(
                "UPDATE users SET must_change_password = true, password = '{noop}old' "
                        + "WHERE id_user = ?",
                pending);
    }

    private String bearer(UUID id, String role) {
        return "Bearer " + tokenFor(id, role);
    }

    @Test
    void director_resendsToAPendingUser_newHashStored_rawOnlyInTheMail() throws Exception {
        String body =
                mvc.perform(
                                post("/api/users/{id}/resend-credentials", pending)
                                        .header("Authorization", bearer(director, "Director")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendWelcomeCredentials(anyString(), anyString(), raw.capture());
        String stored =
                jdbc.queryForObject(
                        "SELECT password FROM users WHERE id_user = ?", String.class, pending);
        assertThat(stored).isNotEqualTo("{noop}old").isNotEqualTo(raw.getValue());
        assertThat(passwordEncoder.matches(raw.getValue(), stored)).isTrue();
        assertThat(body).doesNotContain(raw.getValue());
        Boolean mustChange =
                jdbc.queryForObject(
                        "SELECT must_change_password FROM users WHERE id_user = ?",
                        Boolean.class,
                        pending);
        assertThat(mustChange).isTrue();
    }

    @Test
    void userWhoAlreadyChangedTheirPassword_returns409_andNothingIsSent() throws Exception {
        jdbc.update("UPDATE users SET must_change_password = false WHERE id_user = ?", pending);

        mvc.perform(
                        post("/api/users/{id}/resend-credentials", pending)
                                .header("Authorization", bearer(director, "Director")))
                .andExpect(status().isConflict());

        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password FROM users WHERE id_user = ?",
                                String.class,
                                pending))
                .isEqualTo("{noop}old");
    }

    @Test
    void inactiveUser_returns409() throws Exception {
        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", pending);

        mvc.perform(
                        post("/api/users/{id}/resend-credentials", pending)
                                .header("Authorization", bearer(director, "Director")))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownUser_returns404() throws Exception {
        mvc.perform(
                        post("/api/users/{id}/resend-credentials", UUID.randomUUID())
                                .header("Authorization", bearer(director, "Director")))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherAndSecretary_get403() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        UUID secretary = seedUser("Secretary", false);

        mvc.perform(
                        post("/api/users/{id}/resend-credentials", pending)
                                .header("Authorization", bearer(teacher, "Teacher")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/api/users/{id}/resend-credentials", pending)
                                .header("Authorization", bearer(secretary, "Secretary")))
                .andExpect(status().isForbidden());

        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void userList_exposesMustChangePasswordPerRow() throws Exception {
        UUID settled = seedUser("Secretary", false);

        mvc.perform(get("/api/users").header("Authorization", bearer(director, "Director")))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[?(@.id=='" + pending + "')].mustChangePassword")
                                .value(true))
                .andExpect(
                        jsonPath("$.content[?(@.id=='" + settled + "')].mustChangePassword")
                                .value(false))
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
