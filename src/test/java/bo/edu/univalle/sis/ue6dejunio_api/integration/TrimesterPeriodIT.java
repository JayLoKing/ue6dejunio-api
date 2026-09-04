package bo.edu.univalle.sis.ue6dejunio_api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD persistence for Director-configured trimester periods (POST/GET /api/trimester-periods).
 * Exercises real Postgres unique/overlap constraints alongside the application-level validation.
 * Requires Docker/Testcontainers; SKIPPED in this environment (Docker unavailable).
 */
class TrimesterPeriodIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private Integer academicYearId;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        // The current gestión, which is the one schema-it.sql configured trimesters for.
        academicYearId = currentAcademicYearId();
    }

    @Test
    void create_unknownAcademicYear_returns404() throws Exception {
        String body = """
            {"id_academic_year": %d, "trimester": 1, "start_date": "2027-02-01", "end_date": "2027-05-31"}
            """.formatted(academicYearId + 100000); // does not exist

        mvc.perform(post("/api/trimester-periods")
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound());
    }

    @Test
    void listByYear_returnsSchemaSeededThreePeriods() throws Exception {
        String body = mvc.perform(get("/api/trimester-periods")
                .param("id_academic_year", academicYearId.toString())
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode arr = json.readTree(body);
        assertThat(arr).hasSize(3);
    }

    @Test
    void create_duplicateYearAndTrimester_returns409() throws Exception {
        String body = """
            {"id_academic_year": %d, "trimester": 1, "start_date": "2026-02-01", "end_date": "2026-05-31"}
            """.formatted(academicYearId);

        mvc.perform(post("/api/trimester-periods")
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void create_overlappingRange_returns409() throws Exception {
        // A fresh academic year with only T1 configured, so the overlap check (not the
        // duplicate (year, trimester) check) is what fires for a distinct trimester 2 request.
        Integer freshYearId = jdbc.queryForObject(
            "INSERT INTO academic_years (year) VALUES (2999) RETURNING id_academic_year", Integer.class);
        jdbc.update(
            "INSERT INTO academic_trimesters (id_academic_year, trimester, start_date, end_date) "
                + "VALUES (?, 1, DATE '2026-02-01', DATE '2026-05-31')",
            freshYearId);

        // 05-31 overlaps T1's end date by one day.
        String body = """
            {"id_academic_year": %d, "trimester": 2, "start_date": "2026-05-31", "end_date": "2026-08-31"}
            """.formatted(freshYearId);

        mvc.perform(post("/api/trimester-periods")
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void nonDirector_forbidden() throws Exception {
        UUID teacher = seedUser("Teacher", false);

        mvc.perform(get("/api/trimester-periods")
                .param("id_academic_year", academicYearId.toString())
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isForbidden());
    }
}
