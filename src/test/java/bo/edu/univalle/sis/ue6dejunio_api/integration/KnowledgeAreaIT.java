package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The areas of knowledge, administered.
 *
 * <p>Driven through the endpoints because the part worth proving is the one the database owns:
 * {@code subjects.id_area} is ON DELETE RESTRICT, so an area with subjects can only be refused
 * before the delete, never after it.
 */
class KnowledgeAreaIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
    }

    private String asDirector() {
        return "Bearer " + tokenFor(director, "Director");
    }

    private Integer createArea(String name) throws Exception {
        String body =
                mvc.perform(
                                post("/api/knowledge-areas")
                                        .header("Authorization", asDirector())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"" + name + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("id").asInt();
    }

    @Test
    void create_thenReadItBack() throws Exception {
        Integer id = createArea("Area de prueba");

        mvc.perform(get("/api/knowledge-areas/{id}", id).header("Authorization", asDirector()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Area de prueba"));
    }

    /**
     * Naming an area and deciding where it prints are two thoughts; the second one has a default.
     */
    @Test
    void create_withoutAnOrder_landsAfterTheOnesAlreadyThere() throws Exception {
        Integer maxBefore =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(display_order), 0) FROM knowledge_areas",
                        Integer.class);

        Integer id = createArea("Area sin orden");

        Integer order =
                jdbc.queryForObject(
                        "SELECT display_order FROM knowledge_areas WHERE id_area = ?",
                        Integer.class,
                        id);
        assertThat(order).isEqualTo(maxBefore + 1);
    }

    @Test
    void create_duplicateName_returns409() throws Exception {
        createArea("Area repetida");

        mvc.perform(
                        post("/api/knowledge-areas")
                                .header("Authorization", asDirector())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Area repetida\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mvc.perform(
                        post("/api/knowledge-areas")
                                .header("Authorization", asDirector())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_renamesTheArea() throws Exception {
        Integer id = createArea("Nombre viejo");

        mvc.perform(
                        put("/api/knowledge-areas/{id}", id)
                                .header("Authorization", asDirector())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Nombre nuevo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nombre nuevo"));
    }

    /** An edit that says nothing about the order is not asking for it to be reset. */
    @Test
    void update_withoutAnOrder_leavesItWhereItWas() throws Exception {
        Integer id = createArea("Area estable");
        Integer before =
                jdbc.queryForObject(
                        "SELECT display_order FROM knowledge_areas WHERE id_area = ?",
                        Integer.class,
                        id);

        mvc.perform(
                        put("/api/knowledge-areas/{id}", id)
                                .header("Authorization", asDirector())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Area estable renombrada\"}"))
                .andExpect(status().isOk());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT display_order FROM knowledge_areas WHERE id_area = ?",
                                Integer.class,
                                id))
                .isEqualTo(before);
    }

    @Test
    void delete_emptyArea_returns204() throws Exception {
        Integer id = createArea("Area vacia");

        mvc.perform(delete("/api/knowledge-areas/{id}", id).header("Authorization", asDirector()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/knowledge-areas/{id}", id).header("Authorization", asDirector()))
                .andExpect(status().isNotFound());
    }

    /**
     * The one the database would otherwise answer with a 500. Every subject must name an area, so
     * an area with subjects is not free to go, and the school has to be told what to do first.
     */
    @Test
    void delete_areaWithSubjects_returns409() throws Exception {
        Integer id = createArea("Area ocupada");
        jdbc.update(
                "INSERT INTO subjects (id_subject, name, id_area, is_technical, is_active) "
                        + "VALUES (?,?,?,false,true)",
                UUID.randomUUID(),
                "Materia de " + id,
                id);

        mvc.perform(delete("/api/knowledge-areas/{id}", id).header("Authorization", asDirector()))
                .andExpect(status().isConflict());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM knowledge_areas WHERE id_area = ?",
                                Integer.class,
                                id))
                .isEqualTo(1);
    }

    /** The listing is what lays out the plan, so it comes in the order the plan is printed in. */
    @Test
    void list_comesInPrintingOrder() throws Exception {
        String body =
                mvc.perform(
                                get("/api/knowledge-areas")
                                        .param("limit", "50")
                                        .header("Authorization", asDirector()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        var orders = json.readTree(body).get("content").findValues("displayOrder");
        assertThat(orders).isSortedAccordingTo((a, b) -> Integer.compare(a.asInt(), b.asInt()));
    }

    @Test
    void secretary_isRefused() throws Exception {
        mvc.perform(
                        get("/api/knowledge-areas")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + tokenFor(
                                                        seedUser("Secretary", false), "Secretary")))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacher_isRefused() throws Exception {
        mvc.perform(
                        get("/api/knowledge-areas")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + tokenFor(seedUser("Teacher", false), "Teacher")))
                .andExpect(status().isForbidden());
    }
}
