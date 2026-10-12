package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * An address is one account however it is typed. The web lowercases and trims before sending, so an
 * account stored in capitals, or one typed by hand into a request, used to be unreachable or
 * duplicable.
 */
class EmailNormalizationIT extends AbstractIntegrationTest {

    private static final String STORED = "ana@x.com";
    private static final String PASSWORD = "Clave-Segura-1";

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private IEmailService emailService;

    private UUID ana;

    @BeforeEach
    void seed() {
        ana = seedUser("Teacher", false);
        jdbc.update(
                "UPDATE users SET email = ?, password = ? WHERE id_user = ?",
                STORED,
                passwordEncoder.encode(PASSWORD),
                ana);
    }

    /**
     * Casing only: surrounding blanks never reach the service, because {@code @Email} on {@code
     * LoginRequest} refuses a padded address with a 400 first. The web trims before sending, so
     * that refusal only meets hand-written requests.
     */
    @Test
    void login_withMixedCaseEmail_signsInTheLowercaseAccount() throws Exception {
        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "email",
                                                        "Ana@X.com",
                                                        "password",
                                                        PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(STORED));
    }

    @Test
    void create_sameAddressInCapitals_returns409_andAddsNobody() throws Exception {
        UUID director = seedUser("Director", false);
        Integer teacherRole =
                jdbc.queryForObject(
                        "SELECT id_role FROM roles WHERE name = 'Teacher'", Integer.class);

        mvc.perform(
                        post("/api/users")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "ci", "9876543",
                                                        "names", "Ana",
                                                        "lastNames", "Duplicada",
                                                        "email", "ANA@x.com",
                                                        "roleId", teacherRole))))
                .andExpect(status().isConflict());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM users WHERE lower(email) = ?",
                                Integer.class,
                                STORED))
                .isEqualTo(1);
    }

    /** V27's check: a writer that skips the application still cannot store a second spelling. */
    @Test
    void schema_refusesAnAddressThatIsNotNormalized() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE users SET email = ? WHERE id_user = ?",
                                        "Ana@x.com",
                                        ana))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE users SET email = ? WHERE id_user = ?",
                                        " ana@x.com",
                                        ana))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void forgotPassword_withMixedCaseEmail_mailsTheAccountOwner() throws Exception {
        mvc.perform(
                        post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("email", "ANA@X.Com"))))
                .andExpect(status().isOk());

        verify(emailService).sendPasswordReset(eq(STORED), anyString(), anyString());
    }
}
