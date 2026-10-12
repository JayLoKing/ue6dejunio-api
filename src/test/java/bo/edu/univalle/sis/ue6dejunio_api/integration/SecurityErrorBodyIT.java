package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Rejections decided by the security filter chain, before any controller runs.
 *
 * <p>A role rule in {@code SecurityConfig} and a missing or broken bearer token never reach {@code
 * GlobalExceptionHandler}, so they used to come back with an empty body. The web reads {@code
 * message} from every error, and an empty 403 left it with nothing to show. These tests pin the
 * same body shape the handler writes, and keep the {@code WWW-Authenticate} header on a 401.
 */
class SecurityErrorBodyIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    @Test
    void roleRule_refusal_carriesTheErrorBody() throws Exception {
        mvc.perform(
                        get("/api/grades")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + tokenFor(
                                                        seedUser("Secretary", false), "Secretary")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Acceso denegado"))
                .andExpect(jsonPath("$.path").value("/api/grades"));
    }

    @Test
    void missingToken_isUnauthorized_withBodyAndChallenge() throws Exception {
        mvc.perform(get("/api/grades"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("No autenticado o sesión vencida"))
                .andExpect(jsonPath("$.path").value("/api/grades"));
    }

    /** A token the API cannot verify is what an expired session looks like to the web. */
    @Test
    void invalidToken_isUnauthorized_withBodyAndChallenge() throws Exception {
        mvc.perform(get("/api/grades").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("No autenticado o sesión vencida"));
    }

    /** The login's own 401 is the controller's, and keeps its own message. */
    @Test
    void login_badCredentials_keepsItsMessage() throws Exception {
        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"nobody@ue6.test\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }
}
