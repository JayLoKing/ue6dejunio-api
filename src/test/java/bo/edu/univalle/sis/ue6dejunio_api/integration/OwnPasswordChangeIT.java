package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /api/auth/change-password with a wrong current password. The web treats any 401 sent under a
 * live token as an expired session and logs the user out, so a typo here must answer 400 — while a
 * failed login keeps its 401, because there is no session to lose there.
 */
class OwnPasswordChangeIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID me;

    @BeforeEach
    void seed() {
        me = seedUser("Teacher", false);
        jdbc.update(
                "UPDATE users SET password = ? WHERE id_user = ?",
                passwordEncoder.encode("Secreta123"),
                me);
    }

    private String passwordHashOf(UUID id) {
        return jdbc.queryForObject(
                "SELECT password FROM users WHERE id_user = ?", String.class, id);
    }

    private String emailOf(UUID id) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id_user = ?", String.class, id);
    }

    @Test
    void wrongCurrentPassword_returns400WithTheMessage_andThePasswordStays() throws Exception {
        String before = passwordHashOf(me);

        mvc.perform(
                        post("/api/auth/change-password")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"otra\","
                                                + "\"newPassword\":\"NuevaClave1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta"));

        assertThat(passwordHashOf(me)).isEqualTo(before);
    }

    /** The forced first-login change goes through the same endpoint, so it answers 400 as well. */
    @Test
    void wrongCurrentPassword_onTheForcedFirstChange_returns400() throws Exception {
        jdbc.update("UPDATE users SET must_change_password = TRUE WHERE id_user = ?", me);

        mvc.perform(
                        post("/api/auth/change-password")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"otra\","
                                                + "\"newPassword\":\"NuevaClave1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta"));
    }

    @Test
    void loginWithAWrongPassword_stillReturns401() throws Exception {
        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\""
                                                + emailOf(me)
                                                + "\",\"password\":\"OtraClave99\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }
}
