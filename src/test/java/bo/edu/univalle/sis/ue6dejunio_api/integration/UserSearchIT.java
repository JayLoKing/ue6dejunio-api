package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/users?search= — the user list's search box promises "nombre, CI o correo". Every
 * fragment below is one that appears only in the field under test, so a hit proves that field is
 * searched, not that a name happened to contain it.
 */
class UserSearchIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private String suffix;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        suffix = teacher.toString().substring(0, 8);
    }

    private String bearer() {
        return "Bearer " + tokenFor(director, "Director");
    }

    @Test
    void search_byCiFragment_findsTheUser() throws Exception {
        mvc.perform(
                        get("/api/users")
                                .param("search", "ci-" + suffix)
                                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(teacher.toString())));
    }

    @Test
    void search_byEmailFragmentInAnotherCase_findsTheUser() throws Exception {
        mvc.perform(
                        get("/api/users")
                                .param(
                                        "search",
                                        ("user-" + suffix + "@UE6").toUpperCase(Locale.ROOT))
                                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(teacher.toString())));
    }

    @Test
    void search_byNames_stillFindsTheUser() throws Exception {
        mvc.perform(
                        get("/api/users")
                                .param("search", "nombre" + suffix)
                                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(teacher.toString())));
        mvc.perform(
                        get("/api/users")
                                .param("search", "APELLIDO" + suffix.toUpperCase(Locale.ROOT))
                                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(teacher.toString())));
    }

    @Test
    void search_blank_listsEveryoneButTheCaller() throws Exception {
        mvc.perform(
                        get("/api/users")
                                .param("search", " ")
                                .param("limit", "200")
                                .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(teacher.toString())));
    }
}
