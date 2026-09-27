package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Renewing a session through the real filter chain.
 *
 * <p>Exists because the endpoint's safety rests on a file it does not live in: {@code
 * /api/auth/refresh} is deliberately absent from {@code SecurityConfig.PUBLIC_PATHS} so it falls
 * through to {@code anyRequest().authenticated()}. Listed there by mistake it would be reachable
 * with no token, the handler's {@code JwtAuthenticationToken} would arrive null, and a request that
 * is simply not signed in would answer 500. A unit test on the controller cannot see that, because
 * the mistake would be in the chain, not in the handler.
 */
class SessionRefreshIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    @Test
    void refresh_withAValidToken_returnsAFreshToken() throws Exception {
        UUID director = seedUser("Director", false);

        mvc.perform(
                        post("/api/auth/refresh")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.userId").value(director.toString()));
    }

    @Test
    void refresh_withNoToken_returns401() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    /**
     * The half of the work that the endpoint alone does not do: a closed account stops renewing.
     * Refused at authentication now that every request re-reads the account, which is a stronger
     * answer than the service's own check and reaches it first.
     */
    @Test
    void refresh_afterTheAccountWasClosed_isRefused() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        String token = tokenFor(teacher, "Teacher");
        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", teacher);

        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
