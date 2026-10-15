package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/auth/profile: the fields the own-profile form needs, which the token does not carry. */
class OwnProfileReadIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"Director", "Teacher", "Secretary"})
    void eachRole_readsOwnRow_withoutAnyPasswordField(String role) throws Exception {
        UUID me = seedUser(role, false);
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT ci, names, last_names, email, phone FROM users WHERE id_user = ?",
                        me);

        mvc.perform(
                        get("/api/auth/profile")
                                .header("Authorization", "Bearer " + tokenFor(me, role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ci").value(row.get("ci")))
                .andExpect(jsonPath("$.names").value(row.get("names")))
                .andExpect(jsonPath("$.lastNames").value(row.get("last_names")))
                .andExpect(jsonPath("$.email").value(row.get("email")))
                .andExpect(jsonPath("$.phone").value(row.get("phone")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/auth/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUser_returns401() throws Exception {
        UUID me = seedUser("Teacher", false);
        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", me);

        mvc.perform(
                        get("/api/auth/profile")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher")))
                .andExpect(status().isUnauthorized());
    }
}
