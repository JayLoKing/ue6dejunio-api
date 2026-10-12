package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** PUT /api/users/{id} with an email: the Director fixes an address typed wrong at registration. */
class UserEmailEditIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
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

    private static String emailBody(String email) {
        return "{\"email\":\"" + email + "\"}";
    }

    private String emailOf(UUID id) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id_user = ?", String.class, id);
    }

    @Test
    void director_fixesThePendingUsersEmail_andTheCredentialsGoToTheNewAddress() throws Exception {
        mvc.perform(
                        put("/api/users/{id}", pending)
                                .header("Authorization", bearer(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(emailBody("corregido@ue6.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("corregido@ue6.test"))
                .andExpect(jsonPath("$.credentialsResent").value(true));

        assertThat(emailOf(pending)).isEqualTo("corregido@ue6.test");
        verify(emailService)
                .sendWelcomeCredentials(eq("corregido@ue6.test"), anyString(), anyString());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password FROM users WHERE id_user = ?",
                                String.class,
                                pending))
                .isNotEqualTo("{noop}old");
    }

    @Test
    void userWhoAlreadySignedIn_onlyTheEmailChanges() throws Exception {
        jdbc.update("UPDATE users SET must_change_password = false WHERE id_user = ?", pending);

        mvc.perform(
                        put("/api/users/{id}", pending)
                                .header("Authorization", bearer(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(emailBody("corregido@ue6.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialsResent").value(false));

        assertThat(emailOf(pending)).isEqualTo("corregido@ue6.test");
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void emailOfAnotherUser_returns409_andNothingChanges() throws Exception {
        UUID other = seedUser("Secretary", false);
        String before = emailOf(pending);

        mvc.perform(
                        put("/api/users/{id}", pending)
                                .header("Authorization", bearer(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(emailBody(emailOf(other))))
                .andExpect(status().isConflict());

        assertThat(emailOf(pending)).isEqualTo(before);
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void malformedEmail_returns400() throws Exception {
        mvc.perform(
                        put("/api/users/{id}", pending)
                                .header("Authorization", bearer(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(emailBody("no-es-un-correo")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacherAndSecretary_cannotEditSomeoneElsesEmail() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        UUID secretary = seedUser("Secretary", false);
        String before = emailOf(pending);

        mvc.perform(
                        put("/api/users/{id}", pending)
                                .header("Authorization", bearer(teacher, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(emailBody("robado@ue6.test")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/users/{id}", pending)
                                .header("Authorization", bearer(secretary, "Secretary"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(emailBody("robado@ue6.test")))
                .andExpect(status().isForbidden());

        assertThat(emailOf(pending)).isEqualTo(before);
    }
}
