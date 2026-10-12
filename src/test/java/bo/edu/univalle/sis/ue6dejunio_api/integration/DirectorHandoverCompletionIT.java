package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth.IJwtService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The second phase of the handover: the incoming Director replaces the temporary password, the
 * handover closes, the outgoing account is deactivated and its next request is refused. The failure
 * paths matter more than the happy one: none of them may leave the school without an active
 * Director.
 */
class DirectorHandoverCompletionIT extends AbstractIntegrationTest {

    private static final String URL = "/api/users/director-handover";
    private static final String NEW_PASSWORD = "Mine-Own-Pass-9";
    private static final String OLD_PASSWORD = "Old-Password-1";

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IJwtService jwtService;
    @Autowired private IUserDomain userDomain;
    @MockitoBean private IEmailService emailService;

    private UUID outgoing;
    private String outgoingToken;

    @BeforeEach
    void seed() {
        outgoing = seedUser("Director", false);
        outgoingToken = "Bearer " + tokenFor(outgoing, "Director");
        withKnownPassword(outgoing);
    }

    /** The seeded users carry a placeholder, not a hash the encoder can check. */
    private void withKnownPassword(UUID id) {
        jdbc.update(
                "UPDATE users SET password = ? WHERE id_user = ?",
                passwordEncoder.encode(OLD_PASSWORD),
                id);
    }

    private ResultActions openNewHandover() throws Exception {
        return mvc.perform(
                post(URL)
                        .header("Authorization", outgoingToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsString(
                                        Map.of(
                                                "email", "luis.rojas@ue6.test",
                                                "ci", "7654321",
                                                "names", "Luis",
                                                "lastNames", "Rojas"))));
    }

    private ResultActions openPromotion(UUID teacher) throws Exception {
        return mvc.perform(
                post(URL)
                        .header("Authorization", outgoingToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", emailOf(teacher)))));
    }

    /** The temporary password the last welcome mail carried. */
    private String lastMailedPassword(String email) {
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(emailService, org.mockito.Mockito.atLeastOnce())
                .sendWelcomeCredentials(eq(email), anyString(), raw.capture());
        return raw.getAllValues().getLast();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsString(
                                        Map.of("email", email, "password", password))));
    }

    private String loginToken(String email, String password) throws Exception {
        String body =
                login(email, password)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode node = json.readTree(body);
        return "Bearer " + node.get("accessToken").asText();
    }

    private ResultActions changePassword(String token, String current, String next)
            throws Exception {
        return mvc.perform(
                post("/api/auth/change-password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                json.writeValueAsString(
                                        Map.of("currentPassword", current, "newPassword", next))));
    }

    private String emailOf(UUID id) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id_user = ?", String.class, id);
    }

    private boolean activeOf(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT is_active FROM users WHERE id_user = ?", Boolean.class, id));
    }

    private String roleOf(UUID id) {
        return jdbc.queryForObject(
                "SELECT r.name FROM users u JOIN roles r ON r.id_role = u.id_role "
                        + "WHERE u.id_user = ?",
                String.class,
                id);
    }

    private int activeDirectors() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM users u JOIN roles r ON r.id_role = u.id_role "
                        + "WHERE r.name = 'Director' AND u.is_active",
                Integer.class);
    }

    private boolean handoverPending() {
        return jdbc.queryForObject(
                        "SELECT count(*) FROM director_handovers "
                                + "WHERE closed_at IS NULL AND cancelled_at IS NULL",
                        Integer.class)
                == 1;
    }

    private UUID idByEmail(String email) {
        return jdbc.queryForObject("SELECT id_user FROM users WHERE email = ?", UUID.class, email);
    }

    // ---- the whole story, with a new account ------------------------------------------------

    @Test
    void newDirector_logsInWithTheTemporaryPassword_changesIt_andTheOutgoingIsLockedOut()
            throws Exception {
        openNewHandover().andExpect(status().isCreated());
        String email = "luis.rojas@ue6.test";
        UUID incoming = idByEmail(email);
        String temporary = lastMailedPassword(email);

        // Phase one: both Directors work.
        String incomingToken = loginToken(email, temporary);
        mvc.perform(get("/api/users").header("Authorization", outgoingToken))
                .andExpect(status().isOk());
        assertThat(activeOf(outgoing)).isTrue();
        assertThat(handoverPending()).isTrue();
        assertThat(activeDirectors()).isEqualTo(2);

        // Phase two: the incoming Director sets a password of their own.
        changePassword(incomingToken, temporary, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(handoverPending()).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM director_handovers "
                                        + "WHERE closed_at IS NOT NULL AND cancelled_at IS NULL",
                                Integer.class))
                .isEqualTo(1);
        assertThat(activeOf(outgoing)).isFalse();
        assertThat(activeOf(incoming)).isTrue();
        assertThat(roleOf(incoming)).isEqualTo("Director");
        assertThat(activeDirectors()).isEqualTo(1);

        // The outgoing session ends on the very next request, token still inside its window.
        mvc.perform(get("/api/users").header("Authorization", outgoingToken))
                .andExpect(status().isUnauthorized());
        login(emailOf(outgoing), OLD_PASSWORD).andExpect(status().isForbidden());

        // The new Director signs in with their own password and runs the school.
        String freshToken = loginToken(email, NEW_PASSWORD);
        mvc.perform(get("/api/users").header("Authorization", freshToken))
                .andExpect(status().isOk());
        mvc.perform(get(URL).header("Authorization", freshToken)).andExpect(status().isNoContent());
    }

    @Test
    void promotedTeacher_changesThePassword_andBecomesTheDirector() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        openPromotion(teacher).andExpect(status().isCreated());
        String email = emailOf(teacher);
        String temporary = lastMailedPassword(email);

        changePassword(loginToken(email, temporary), temporary, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        assertThat(handoverPending()).isFalse();
        assertThat(activeOf(outgoing)).isFalse();
        assertThat(roleOf(teacher)).isEqualTo("Director");
        assertThat(activeDirectors()).isEqualTo(1);
        String token = loginToken(email, NEW_PASSWORD);
        mvc.perform(get("/api/users").header("Authorization", token)).andExpect(status().isOk());
    }

    // ---- the other way to set a password ----------------------------------------------------

    @Test
    void resettingThePasswordThroughTheRecoveryLink_closesTheHandoverToo() throws Exception {
        openNewHandover().andExpect(status().isCreated());
        String email = "luis.rojas@ue6.test";
        UUID incoming = idByEmail(email);
        String resetToken = jwtService.issuePasswordResetToken(userDomain.findById(incoming).get());

        mvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "token", resetToken,
                                                        "newPassword", NEW_PASSWORD))))
                .andExpect(status().isNoContent());

        assertThat(handoverPending()).isFalse();
        assertThat(activeOf(outgoing)).isFalse();
        assertThat(activeOf(incoming)).isTrue();
        assertThat(activeDirectors()).isEqualTo(1);
        loginToken(email, NEW_PASSWORD);
    }

    // ---- what must NOT close it -------------------------------------------------------------

    @Test
    void aWrongCurrentPassword_changesNothing_andTheOutgoingStaysActive() throws Exception {
        openNewHandover().andExpect(status().isCreated());
        String email = "luis.rojas@ue6.test";
        String temporary = lastMailedPassword(email);
        String incomingToken = loginToken(email, temporary);

        // 400, not 401: under a live token the web reads a 401 as an expired session.
        changePassword(incomingToken, "not-the-password", NEW_PASSWORD)
                .andExpect(status().isBadRequest());

        assertThat(handoverPending()).isTrue();
        assertThat(activeOf(outgoing)).isTrue();
        assertThat(activeDirectors()).isEqualTo(2);
    }

    @Test
    void anotherUserChangingTheirPassword_doesNotCloseTheHandover() throws Exception {
        openNewHandover().andExpect(status().isCreated());
        UUID bystander = seedUser("Teacher", false);
        withKnownPassword(bystander);

        changePassword("Bearer " + tokenFor(bystander, "Teacher"), OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        assertThat(handoverPending()).isTrue();
        assertThat(activeOf(outgoing)).isTrue();
    }

    @Test
    void theOutgoingChangingTheirOwnPassword_doesNotCloseTheHandover() throws Exception {
        openNewHandover().andExpect(status().isCreated());

        changePassword(outgoingToken, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(handoverPending()).isTrue();
        assertThat(activeOf(outgoing)).isTrue();
    }

    @Test
    void anIncomingWhoseRoleWasChangedMeanwhile_changesTheirPassword_butTheOutgoingIsKept()
            throws Exception {
        openNewHandover().andExpect(status().isCreated());
        String email = "luis.rojas@ue6.test";
        UUID incoming = idByEmail(email);
        String temporary = lastMailedPassword(email);
        String incomingToken = loginToken(email, temporary);
        // The Director edits the incoming account into a Teacher before it ever signs in.
        jdbc.update(
                "UPDATE users SET id_role = (SELECT id_role FROM roles WHERE name = 'Teacher') "
                        + "WHERE id_user = ?",
                incoming);

        changePassword(incomingToken, temporary, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(handoverPending()).isTrue();
        assertThat(activeOf(outgoing)).isTrue();
        assertThat(activeDirectors()).isEqualTo(1);
        mvc.perform(get("/api/users").header("Authorization", outgoingToken))
                .andExpect(status().isOk());
    }

    @Test
    void anIncomingDeactivatedMeanwhile_cannotChangeThePassword_andTheOutgoingIsKept()
            throws Exception {
        openNewHandover().andExpect(status().isCreated());
        String email = "luis.rojas@ue6.test";
        UUID incoming = idByEmail(email);
        String temporary = lastMailedPassword(email);
        String incomingToken = loginToken(email, temporary);
        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", incoming);

        changePassword(incomingToken, temporary, NEW_PASSWORD).andExpect(status().isUnauthorized());

        assertThat(handoverPending()).isTrue();
        assertThat(activeOf(outgoing)).isTrue();
    }

    // ---- after it is over -------------------------------------------------------------------

    @Test
    void afterClosing_thereIsNothingToCancel_andTheNewDirectorCanStartAnotherHandover()
            throws Exception {
        openNewHandover().andExpect(status().isCreated());
        String email = "luis.rojas@ue6.test";
        String temporary = lastMailedPassword(email);
        changePassword(loginToken(email, temporary), temporary, NEW_PASSWORD)
                .andExpect(status().isNoContent());
        String newDirector = loginToken(email, NEW_PASSWORD);

        mvc.perform(delete(URL).header("Authorization", newDirector))
                .andExpect(status().isNotFound());
        mvc.perform(delete(URL).header("Authorization", outgoingToken))
                .andExpect(status().isUnauthorized());
        UUID secretary = seedUser("Secretary", false);
        mvc.perform(
                        post(URL)
                                .header("Authorization", newDirector)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("email", emailOf(secretary)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outgoing.email").value(email));
    }
}
