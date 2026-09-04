package bo.edu.univalle.sis.ue6dejunio_api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The gestión list behind the student directory's year filter.
 *
 * <p>Read-only and open to anyone signed in, like the rest of {@code /api/catalog}: the secretariat
 * picks a gestión to read, and administering years is not on offer here at all.
 */
class CatalogAcademicYearsIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    /**
     * Newest first, because the year a school is working in is the one it opens the list to pick,
     * and every year before it is history the user scrolls back to.
     */
    @Test
    void academicYears_areListedNewestFirst() throws Exception {
        mvc.perform(get("/api/catalog/academic-years")
                .header("Authorization", "Bearer " + tokenFor(
                    seedUser("Director", false), "Director")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].year").value(2026))
            .andExpect(jsonPath("$[1].year").value(2025));
    }

    @Test
    void academicYears_secretaryReads() throws Exception {
        mvc.perform(get("/api/catalog/academic-years")
                .header("Authorization", "Bearer " + tokenFor(
                    seedUser("Secretary", false), "Secretary")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].year").exists());
    }

    @Test
    void academicYears_withoutAToken_refused() throws Exception {
        mvc.perform(get("/api/catalog/academic-years"))
            .andExpect(status().isUnauthorized());
    }
}
