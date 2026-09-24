package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec: what the homeroom teacher may do with a technical subject of their own course — Música,
 * Religión, Técnica Tecnológica — when somebody else was put in charge of teaching it.
 *
 * <p>Read yes, write no. The homeroom teacher signs the libreta, the centralizador and the informe
 * pedagógico of that classroom and every one of them quotes these marks, so the records have to be
 * reachable; recording them stays with the teacher the Director assigned.
 *
 * <p>Driven through MockMvc and not the unit test alone because the guard now resolves the class
 * group through {@code findById}, a different adapter query than the one the write path uses. A
 * mocked port would have stayed green whatever that query does with the course association.
 */
class TechnicalSubjectReadAuthorizationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID homeroomTeacher;
    private UUID technicalTeacher;
    private UUID strangerTeacher;
    private UUID musicClassGroup;
    private UUID musicCriterion;

    @BeforeEach
    void seed() {
        homeroomTeacher = seedUser("Teacher", false);
        technicalTeacher = seedUser("Teacher", true);
        strangerTeacher = seedUser("Teacher", false);

        seedTechnicalSubject("Educacion Musical");
        UUID course = seedCourse(homeroomTeacher, "B");
        musicClassGroup = seedClassGroup(course, technicalTeacher, "Educacion Musical");
        musicCriterion = seedCriterion(musicClassGroup, 1, "Knowing", "Lectura de partitura");
    }

    /**
     * The IT schema seeds only Matematicas and Lenguaje, neither of them technical. The flag never
     * reaches the guard — the rule is structural, about who runs the class group and who runs the
     * course — but the subject is seeded as technical anyway so the fixture reads as the situation
     * the teacher actually reported.
     */
    private void seedTechnicalSubject(String name) {
        jdbc.update(
                "INSERT INTO subjects (name, id_area, is_technical) "
                        + "SELECT ?, id_area, true FROM knowledge_areas WHERE name = ? "
                        + "ON CONFLICT DO NOTHING",
                name,
                "Cosmos y Pensamiento");
    }

    @Test
    void homeroomTeacher_listsTheCriteriaOfATechnicalSubjectSomebodyElseTeaches() throws Exception {
        // The screen that used to answer "Acceso denegado / Código 403".
        mvc.perform(
                        get("/api/criteria")
                                .param("id_class_group", musicClassGroup.toString())
                                .param("trimester", "1")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher")))
                .andExpect(status().isOk());
    }

    @Test
    void homeroomTeacher_readsTheMarksOfATechnicalSubjectSomebodyElseTeaches() throws Exception {
        mvc.perform(
                        get("/api/assessment-scores/criterion/" + musicCriterion)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher")))
                .andExpect(status().isOk());

        mvc.perform(
                        get("/api/criteria/" + musicCriterion)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher")))
                .andExpect(status().isOk());
    }

    @Test
    void homeroomTeacher_cannotCreateACriterionInATechnicalSubjectSomebodyElseTeaches()
            throws Exception {
        // Read-only means read-only. The teacher in charge of the subject defines what it
        // evaluates.
        String body =
                """
            {"id_class_group":"%s","trimester":1,"dimension":"Knowing","name":"Mia"}
            """
                        .formatted(musicClassGroup);

        mvc.perform(
                        post("/api/criteria")
                                .contentType("application/json")
                                .content(body)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher")))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicalTeacher_stillOwnsTheSubjectTheyWerePutInChargeOf() throws Exception {
        mvc.perform(
                        get("/api/criteria")
                                .param("id_class_group", musicClassGroup.toString())
                                .param("trimester", "1")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(technicalTeacher, "Teacher")))
                .andExpect(status().isOk());
    }

    @Test
    void teacherOfAnotherCourse_reachesNeitherTheCriteriaNorTheMarks() throws Exception {
        // Widening the read for the homeroom teacher must not have opened it for everybody holding
        // the Teacher role.
        mvc.perform(
                        get("/api/criteria")
                                .param("id_class_group", musicClassGroup.toString())
                                .param("trimester", "1")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(strangerTeacher, "Teacher")))
                .andExpect(status().isForbidden());

        mvc.perform(
                        get("/api/assessment-scores/criterion/" + musicCriterion)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(strangerTeacher, "Teacher")))
                .andExpect(status().isForbidden());
    }

    @Test
    void homeroomTeacherAssignedTheTechnicalSubject_writesItLikeAnyOther() throws Exception {
        // The case the Director creates when they put the homeroom teacher in charge of the
        // technical subject too: they own the class group outright, so the write guard lets them
        // through on its own and nothing here is special-cased.
        UUID ownCourse = seedCourse(homeroomTeacher, "C");
        UUID ownMusic = seedClassGroup(ownCourse, homeroomTeacher, "Educacion Musical");
        String body =
                """
            {"id_class_group":"%s","trimester":1,"dimension":"Knowing","name":"Mia"}
            """
                        .formatted(ownMusic);

        mvc.perform(
                        post("/api/criteria")
                                .contentType("application/json")
                                .content(body)
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(homeroomTeacher, "Teacher")))
                .andExpect(status().isOk());
    }
}
