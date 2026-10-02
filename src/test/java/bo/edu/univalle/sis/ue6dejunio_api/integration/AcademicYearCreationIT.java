package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The Director opens a new gestión ahead of time; nobody else does, and asking twice is safe. */
class AcademicYearCreationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private String bearer(String role) {
        return "Bearer " + tokenFor(seedUser(role, false), role);
    }

    private ResultActions create(String authorization, Object year) throws Exception {
        var request =
                post("/api/academic-years")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("year", year)));
        if (authorization != null) {
            request = request.header("Authorization", authorization);
        }
        return mvc.perform(request);
    }

    private int countOf(int year) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM academic_years WHERE year = ?", Integer.class, year);
    }

    @Test
    void director_createsANewYear_inactiveAndOpen_with201() throws Exception {
        create(bearer("Director"), 2027)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.year").value(2027))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(countOf(2027)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT year FROM academic_years WHERE active", Integer.class))
                .isEqualTo(2026);
    }

    @Test
    void creatingTheSameYearAgain_returnsTheExistingOneWith200() throws Exception {
        create(bearer("Director"), 2027).andExpect(status().isCreated());
        Integer id = academicYearId(2027);

        create(bearer("Director"), 2027)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.year").value(2027));

        assertThat(countOf(2027)).isEqualTo(1);
    }

    @Test
    void creatingAnExistingActiveYear_changesNothing() throws Exception {
        create(bearer("Director"), 2026)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void yearOutOfRange_isRejectedWith400() throws Exception {
        create(bearer("Director"), 1999).andExpect(status().isBadRequest());
        create(bearer("Director"), 2101).andExpect(status().isBadRequest());

        assertThat(countOf(1999)).isZero();
    }

    @Test
    void missingYear_isRejectedWith400() throws Exception {
        mvc.perform(
                        post("/api/academic-years")
                                .header("Authorization", bearer("Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacherAndSecretary_forbidden_anonymous_unauthorized() throws Exception {
        create(bearer("Teacher"), 2027).andExpect(status().isForbidden());
        create(bearer("Secretary"), 2027).andExpect(status().isForbidden());
        create(null, 2027).andExpect(status().isUnauthorized());

        assertThat(countOf(2027)).isZero();
    }
}
