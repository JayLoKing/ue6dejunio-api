package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** The Director closes and reopens a year; closing never hands the working year to another. */
class AcademicYearClosingIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private String bearer(String role) {
        return "Bearer " + tokenFor(seedUser(role, false), role);
    }

    private String url(Integer id, String verb) {
        return "/api/academic-years/" + id + "/" + verb;
    }

    private Integer activeCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM academic_years WHERE active", Integer.class);
    }

    private Timestamp closedAt(Integer id) {
        return jdbc.queryForObject(
                "SELECT closed_at FROM academic_years WHERE id_academic_year = ?",
                Timestamp.class,
                id);
    }

    @Test
    void director_closesTheActiveYear_itIsClosedInactiveAndNoYearIsActive() throws Exception {
        Integer id2026 = academicYearId(2026);

        mvc.perform(put(url(id2026, "close")).header("Authorization", bearer("Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.closed").value(true));

        mvc.perform(get("/api/catalog/academic-years").header("Authorization", bearer("Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].year").value(2026))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].closed").value(true))
                .andExpect(jsonPath("$[1].active").value(false));
        assertThat(activeCount()).isZero();
    }

    @Test
    void closingTwice_keepsTheFirstClosingMoment() throws Exception {
        Integer id2026 = academicYearId(2026);
        String auth = bearer("Director");

        mvc.perform(put(url(id2026, "close")).header("Authorization", auth))
                .andExpect(status().isOk());
        Timestamp first = closedAt(id2026);
        mvc.perform(put(url(id2026, "close")).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true));

        assertThat(first).isNotNull();
        assertThat(closedAt(id2026)).isEqualTo(first);
    }

    @Test
    void closedYear_cannotBeActivated_reopenKeepsItInactive_thenActivationWorks() throws Exception {
        Integer id2026 = academicYearId(2026);
        String auth = bearer("Director");
        mvc.perform(put(url(id2026, "close")).header("Authorization", auth))
                .andExpect(status().isOk());

        mvc.perform(put(url(id2026, "activate")).header("Authorization", auth))
                .andExpect(status().isConflict());

        mvc.perform(put(url(id2026, "reopen")).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.closed").value(false));
        assertThat(activeCount()).isZero();

        mvc.perform(put(url(id2026, "activate")).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.closed").value(false));
        assertThat(activeCount()).isEqualTo(1);
    }

    @Test
    void reopeningAnOpenYear_isANoOp() throws Exception {
        Integer id2026 = academicYearId(2026);

        mvc.perform(put(url(id2026, "reopen")).header("Authorization", bearer("Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.closed").value(false));
    }

    @Test
    void unknownYear_is404() throws Exception {
        String auth = bearer("Director");

        mvc.perform(put("/api/academic-years/999999/close").header("Authorization", auth))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/academic-years/999999/reopen").header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherAndSecretary_forbidden_anonymous_unauthorized() throws Exception {
        Integer id2026 = academicYearId(2026);

        for (String verb : new String[] {"close", "reopen"}) {
            mvc.perform(put(url(id2026, verb)).header("Authorization", bearer("Teacher")))
                    .andExpect(status().isForbidden());
            mvc.perform(put(url(id2026, verb)).header("Authorization", bearer("Secretary")))
                    .andExpect(status().isForbidden());
            mvc.perform(put(url(id2026, verb))).andExpect(status().isUnauthorized());
        }

        assertThat(activeCount()).isEqualTo(1);
        assertThat(closedAt(id2026)).isNull();
    }
}
