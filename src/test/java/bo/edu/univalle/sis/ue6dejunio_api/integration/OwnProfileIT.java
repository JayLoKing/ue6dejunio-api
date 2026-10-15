package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** PUT /api/auth/profile: whoever is signed in edits their own names and phone, nothing else. */
class OwnProfileIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private static final String BODY =
            "{\"names\":\"Maria\",\"lastNames\":\"Lopez\",\"phone\":\"71111111\"}";

    @ParameterizedTest
    @ValueSource(strings = {"Director", "Teacher", "Secretary"})
    void eachRole_updatesOwnNamesLastNamesAndPhone_leavingCiEmailAndRoleAlone(String role)
            throws Exception {
        UUID me = seedUser(role, false);
        Map<String, Object> before =
                jdbc.queryForMap("SELECT ci, email, id_role FROM users WHERE id_user = ?", me);

        mvc.perform(
                        put("/api/auth/profile")
                                .header("Authorization", "Bearer " + tokenFor(me, role))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(me.toString()))
                .andExpect(jsonPath("$.fullName").value("Maria Lopez"))
                .andExpect(jsonPath("$.role").value(role));

        Map<String, Object> after =
                jdbc.queryForMap(
                        "SELECT ci, email, id_role, names, last_names, phone FROM users "
                                + "WHERE id_user = ?",
                        me);
        assertThat(after.get("names")).isEqualTo("Maria");
        assertThat(after.get("last_names")).isEqualTo("Lopez");
        assertThat(after.get("phone")).isEqualTo("71111111");
        assertThat(after.get("ci")).isEqualTo(before.get("ci"));
        assertThat(after.get("email")).isEqualTo(before.get("email"));
        assertThat(after.get("id_role")).isEqualTo(before.get("id_role"));
    }

    @Test
    void anotherUsersPhone_isAConflictAndChangesNothing() throws Exception {
        UUID me = seedUser("Teacher", false);
        UUID colleague = seedUser("Teacher", false);
        String mine =
                jdbc.queryForObject("SELECT phone FROM users WHERE id_user = ?", String.class, me);
        String theirs =
                jdbc.queryForObject(
                        "SELECT phone FROM users WHERE id_user = ?", String.class, colleague);

        mvc.perform(
                        put("/api/auth/profile")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"names\":\"Maria\",\"lastNames\":\"Lopez\",\"phone\":\""
                                                + theirs
                                                + "\"}"))
                .andExpect(status().isConflict());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT phone FROM users WHERE id_user = ?", String.class, me))
                .isEqualTo(mine);
    }

    /** Unknown fields are ignored by Jackson, so an email or role in the body changes nothing. */
    @Test
    void emailRoleAndActiveInTheBody_areIgnored() throws Exception {
        UUID me = seedUser("Teacher", false);
        Map<String, Object> before =
                jdbc.queryForMap("SELECT email, id_role FROM users WHERE id_user = ?", me);

        mvc.perform(
                        put("/api/auth/profile")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"names\":\"Maria\",\"lastNames\":\"Lopez\","
                                                + "\"email\":\"evil@ue6.test\",\"roleId\":1,"
                                                + "\"role\":\"Director\",\"active\":false,"
                                                + "\"ci\":\"99999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("Teacher"));

        Map<String, Object> after =
                jdbc.queryForMap(
                        "SELECT email, id_role, ci, is_active FROM users WHERE id_user = ?", me);
        assertThat(after.get("email")).isEqualTo(before.get("email"));
        assertThat(after.get("id_role")).isEqualTo(before.get("id_role"));
        assertThat(after.get("ci")).isNotEqualTo("99999999");
        assertThat(after.get("is_active")).isEqualTo(true);
    }

    @Test
    void blankNames_returns400() throws Exception {
        UUID me = seedUser("Teacher", false);

        mvc.perform(
                        put("/api/auth/profile")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"names\":\" \",\"lastNames\":\"Lopez\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedPhone_returns400() throws Exception {
        UUID me = seedUser("Teacher", false);

        mvc.perform(
                        put("/api/auth/profile")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"names\":\"Maria\",\"lastNames\":\"Lopez\","
                                                + "\"phone\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticated_returns401() throws Exception {
        mvc.perform(put("/api/auth/profile").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUser_returns401() throws Exception {
        UUID me = seedUser("Teacher", false);
        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", me);

        mvc.perform(
                        put("/api/auth/profile")
                                .header("Authorization", "Bearer " + tokenFor(me, "Teacher"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
