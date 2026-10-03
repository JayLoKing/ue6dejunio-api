package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The admin subject list takes a {@code status} filter so the Director can find a deactivated
 * subject and bring it back. The default stays "active" so every existing caller reads what it
 * always read, and the catalogue the pickers use never learns about the filter.
 */
class SubjectStatusFilterIT extends AbstractIntegrationTest {

    // The schema is shared across tests and the name is unique, so every test seeds its own pair.
    private String activeName;
    private String inactiveName;

    @Autowired private MockMvc mvc;

    private String director;

    @BeforeEach
    void seed() {
        director = "Bearer " + tokenFor(seedUser("Director", false), "Director");
        String tag = UUID.randomUUID().toString().substring(0, 8);
        activeName = "Filtro-Activa-" + tag;
        inactiveName = "Filtro-Inactiva-" + tag;
        insertSubject(activeName, true);
        insertSubject(inactiveName, false);
    }

    private void insertSubject(String name, boolean active) {
        jdbc.update(
                "INSERT INTO subjects (id_subject, name, id_area, is_active) "
                        + "SELECT ?, ?, id_area, ? FROM knowledge_areas LIMIT 1",
                UUID.randomUUID(),
                name,
                active);
    }

    private String names(String query) throws Exception {
        return mvc.perform(get("/api/subjects" + query).header("Authorization", director))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    void default_returnsOnlyActiveSubjects() throws Exception {
        String body = names("?limit=200");
        org.assertj.core.api.Assertions.assertThat(body)
                .contains(activeName)
                .doesNotContain(inactiveName);
    }

    @Test
    void statusActive_returnsOnlyActiveSubjects() throws Exception {
        String body = names("?limit=200&status=active");
        org.assertj.core.api.Assertions.assertThat(body)
                .contains(activeName)
                .doesNotContain(inactiveName);
    }

    @Test
    void statusInactive_returnsOnlyInactiveSubjects() throws Exception {
        String body = names("?limit=200&status=inactive");
        org.assertj.core.api.Assertions.assertThat(body)
                .contains(inactiveName)
                .doesNotContain(activeName);
    }

    @Test
    void statusAll_returnsBoth() throws Exception {
        String body = names("?limit=200&status=all");
        org.assertj.core.api.Assertions.assertThat(body)
                .contains(activeName)
                .contains(inactiveName);
    }

    @Test
    void unknownStatus_isABadRequest() throws Exception {
        mvc.perform(get("/api/subjects?status=archived").header("Authorization", director))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void catalogueSubjects_neverIncludeAnInactiveOne() throws Exception {
        String body =
                mvc.perform(get("/api/catalog/subjects").header("Authorization", director))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .contains(activeName)
                .doesNotContain(inactiveName);
    }
}
