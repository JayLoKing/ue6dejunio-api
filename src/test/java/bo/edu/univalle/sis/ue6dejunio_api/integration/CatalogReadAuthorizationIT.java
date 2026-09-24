package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The grade and parallel lists behind the student directory's filters.
 *
 * <p>The secretariat reads the directory but administers nothing, so it is barred from {@code
 * /api/grades/**} and {@code /api/parallels/**} — those are the Director's CRUD. The dropdowns are
 * fed from {@code /api/catalog}, which is open to anyone signed in, and these tests exist so that
 * stays true: narrowing the catalog would leave the Secretary staring at two empty filters with no
 * failing test to say why.
 */
class CatalogReadAuthorizationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    @Test
    void grades_secretary_reads() throws Exception {
        mvc.perform(
                        get("/api/catalog/grades")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + tokenFor(
                                                        seedUser("Secretary", false), "Secretary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void parallels_secretary_reads() throws Exception {
        mvc.perform(
                        get("/api/catalog/parallels")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + tokenFor(
                                                        seedUser("Secretary", false), "Secretary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    /** Administering the catalogue stays where it was: the Director's alone. */
    @Test
    void gradesCrud_secretary_refused() throws Exception {
        mvc.perform(
                        get("/api/grades")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + tokenFor(
                                                        seedUser("Secretary", false), "Secretary")))
                .andExpect(status().isForbidden());
    }

    @Test
    void parallelsCrud_secretary_refused() throws Exception {
        mvc.perform(
                        get("/api/parallels")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + tokenFor(
                                                        seedUser("Secretary", false), "Secretary")))
                .andExpect(status().isForbidden());
    }
}
