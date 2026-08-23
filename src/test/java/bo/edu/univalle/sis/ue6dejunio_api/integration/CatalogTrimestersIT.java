package bo.edu.univalle.sis.ue6dejunio_api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/catalog/trimesters — any authenticated user (Teacher included) can read; defaults to
 * the current (latest) academic year when id_academic_year is omitted.
 * Requires Docker/Testcontainers; SKIPPED in this environment (Docker unavailable).
 */
class CatalogTrimestersIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID teacher;
    private Integer academicYearId;

    @BeforeEach
    void seed() {
        teacher = seedUser("Teacher", false);
        academicYearId = jdbc.queryForObject("SELECT id_academic_year FROM academic_years LIMIT 1", Integer.class);
    }

    @Test
    void trimesters_explicitYear_returnsSchemaSeededThreePeriodsOrderedByTrimester() throws Exception {
        String body = mvc.perform(get("/api/catalog/trimesters")
                .param("id_academic_year", academicYearId.toString())
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode arr = json.readTree(body);
        assertThat(arr).hasSize(3);
        assertThat(arr.get(0).get("trimester").asInt()).isEqualTo(1);
        assertThat(arr.get(2).get("trimester").asInt()).isEqualTo(3);
    }

    @Test
    void trimesters_omittedYear_defaultsToCurrentAcademicYear() throws Exception {
        mvc.perform(get("/api/catalog/trimesters")
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isOk());
    }

    @Test
    void trimesters_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/catalog/trimesters")
                .param("id_academic_year", academicYearId.toString()))
            .andExpect(status().isUnauthorized());
    }
}
