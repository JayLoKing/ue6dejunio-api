package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /api/auth/change-email: a signed-in user moves their own account to another address, proving
 * it is them with the current password. The token's subject is the user id, so the session survives
 * the move; the answer is a fresh token whose email claim already reads the new address.
 */
class OwnEmailChangeIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private IEmailService emailService;

    private UUID me;

    @BeforeEach
    void seed() {
        me = seedUser("Teacher", false);
        jdbc.update(
                "UPDATE users SET password = ? WHERE id_user = ?",
                passwordEncoder.encode("Secreta123"),
                me);
    }

    private String emailOf(UUID id) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id_user = ?", String.class, id);
    }

    private static String body(String newEmail, String currentPassword) {
        return "{\"newEmail\":\""
                + newEmail
                + "\",\"currentPassword\":\""
                + currentPassword
                + "\"}";
    }

    @Test
    void rightPassword_movesTheAddress_andTheNewTokenStillWorks() throws Exception {
        String answer =
                mvc.perform(
                                post("/api/auth/change-email")
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(me, "Teacher"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body("mio@ue6.test", "Secreta123")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.userId").value(me.toString()))
                        .andExpect(jsonPath("$.email").value("mio@ue6.test"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(emailOf(me)).isEqualTo("mio@ue6.test");
        JsonNode session = json.readTree(answer);
        mvc.perform(
                        get("/api/auth/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + session.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("mio@ue6.test"));
    }

    /** Login looks the account up by address: the new one opens it, the old one no longer does. */
    @Test
    void afterTheChange_loginTakesTheNewAddressAndRefusesTheOld() throws Exception {
        String old = emailOf(me);
        mvc.perform(
                        post("/api/auth/change-email")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("mio@ue6.test", "Secreta123")))
                .andExpect(status().isOk());

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"mio@ue6.test\",\"password\":\"Secreta123\"}"))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + old + "\",\"password\":\"Secreta123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void afterTheChange_passwordRecoveryMailsTheNewAddress() throws Exception {
        mvc.perform(
                        post("/api/auth/change-email")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("mio@ue6.test", "Secreta123")))
                .andExpect(status().isOk());

        mvc.perform(
                        post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"mio@ue6.test\"}"))
                .andExpect(status().isOk());

        verify(emailService).sendPasswordReset(eq("mio@ue6.test"), anyString(), anyString());
    }

    @Test
    void wrongPassword_returns400_andTheAddressStays() throws Exception {
        String before = emailOf(me);

        mvc.perform(
                        post("/api/auth/change-email")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("mio@ue6.test", "otra")))
                .andExpect(status().isBadRequest());

        assertThat(emailOf(me)).isEqualTo(before);
    }

    @Test
    void addressOfAnotherUser_returns409() throws Exception {
        UUID other = seedUser("Secretary", false);
        String before = emailOf(me);

        mvc.perform(
                        post("/api/auth/change-email")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(emailOf(other), "Secreta123")))
                .andExpect(status().isConflict());

        assertThat(emailOf(me)).isEqualTo(before);
    }

    @Test
    void sameAddressAsNow_returns400() throws Exception {
        mvc.perform(
                        post("/api/auth/change-email")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(emailOf(me), "Secreta123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedAddress_returns400() throws Exception {
        mvc.perform(
                        post("/api/auth/change-email")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("no-es-un-correo", "Secreta123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mvc.perform(
                        post("/api/auth/change-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("mio@ue6.test", "Secreta123")))
                .andExpect(status().isUnauthorized());
    }

    /** Same throttle as change-password: both take the current password and could be guessed at. */
    @Test
    void repeatedAttempts_areThrottled() throws Exception {
        String bearer = "Bearer " + tokenFor(me, "Teacher");
        int lastStatus = 0;
        for (int i = 0; i < 10 && lastStatus != 429; i++) {
            lastStatus =
                    mvc.perform(
                                    post("/api/auth/change-email")
                                            .header("Authorization", bearer)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(body("mio@ue6.test", "otra")))
                            .andReturn()
                            .getResponse()
                            .getStatus();
        }

        assertThat(lastStatus).isEqualTo(429);
    }
}
