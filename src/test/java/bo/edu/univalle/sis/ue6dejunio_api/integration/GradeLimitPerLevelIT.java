package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The six-grade limit belongs to a level, not to the table. Levels and grades are catalog tables
 * that the base class never truncates, so this class makes its own two levels and removes them.
 */
class GradeLimitPerLevelIT extends AbstractIntegrationTest {

    private static final String LEVEL_A = "IT Limite A";
    private static final String LEVEL_B = "IT Limite B";

    @Autowired private MockMvc mvc;

    private String bearer;
    private Integer levelA;
    private Integer levelB;

    @BeforeEach
    void seed() {
        UUID director = seedUser("Director", false);
        bearer = "Bearer " + tokenFor(director, "Director");
        levelA = insertLevel(LEVEL_A);
        levelB = insertLevel(LEVEL_B);
    }

    @AfterEach
    void removeCatalogRows() {
        jdbc.update(
                "DELETE FROM grades WHERE id_level IN "
                        + "(SELECT id_level FROM levels WHERE name IN (?, ?))",
                LEVEL_A,
                LEVEL_B);
        jdbc.update("DELETE FROM levels WHERE name IN (?, ?)", LEVEL_A, LEVEL_B);
    }

    private Integer insertLevel(String name) {
        return jdbc.queryForObject(
                "INSERT INTO levels (name) VALUES (?) RETURNING id_level", Integer.class, name);
    }

    private void fillLevel(Integer levelId, int count) {
        for (int i = 1; i <= count; i++) {
            jdbc.update("INSERT INTO grades (id_level, name) VALUES (?, ?)", levelId, "G" + i);
        }
    }

    private int gradesIn(Integer levelId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM grades WHERE id_level = ?", Integer.class, levelId);
    }

    private String body(String name, Integer levelId) {
        return "{\"name\": \"%s\", \"id_level\": %d}".formatted(name, levelId);
    }

    @Test
    void create_secondLevelStillAcceptsGradesWhenTheFirstIsFull() throws Exception {
        fillLevel(levelA, 6);

        mvc.perform(
                        post("/api/grades")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Primero", levelB)))
                .andExpect(status().isOk());

        assertThat(gradesIn(levelB)).isEqualTo(1);
    }

    @Test
    void create_seventhGradeInTheSameLevel_returns409() throws Exception {
        fillLevel(levelA, 6);

        mvc.perform(
                        post("/api/grades")
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Septimo", levelA)))
                .andExpect(status().isConflict());

        assertThat(gradesIn(levelA)).isEqualTo(6);
    }

    @Test
    void update_movingAGradeIntoAFullLevel_returns409() throws Exception {
        fillLevel(levelA, 6);
        fillLevel(levelB, 1);
        Integer moving =
                jdbc.queryForObject(
                        "SELECT id_grade FROM grades WHERE id_level = ?", Integer.class, levelB);

        mvc.perform(
                        put("/api/grades/" + moving)
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Movido", levelA)))
                .andExpect(status().isConflict());

        assertThat(gradesIn(levelA)).isEqualTo(6);
        assertThat(gradesIn(levelB)).isEqualTo(1);
    }

    @Test
    void update_renamingInsideAFullLevel_isStillAllowed() throws Exception {
        fillLevel(levelA, 6);
        Integer target =
                jdbc.queryForObject(
                        "SELECT id_grade FROM grades WHERE id_level = ? AND name = 'G1'",
                        Integer.class,
                        levelA);

        mvc.perform(
                        put("/api/grades/" + target)
                                .header("Authorization", bearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Renombrado", levelA)))
                .andExpect(status().isOk());
    }
}
