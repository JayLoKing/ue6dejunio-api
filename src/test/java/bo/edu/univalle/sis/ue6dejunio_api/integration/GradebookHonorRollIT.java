package bo.edu.univalle.sis.ue6dejunio_api.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The cuadro de honor over a real Postgres, both scopes of it.
 *
 * <p>The ranking is unit-tested against mocked ports, and those mocks hand back the totals the test
 * itself wrote. What only a database can answer is whether the podium is ordered by the same
 * {@code total_score} the school's sheets print — a generated column this code never writes — and
 * whether the whole-school scope really reaches across courses instead of reading one.
 */
class GradebookHonorRollIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private UUID courseA;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);

        courseA = seedCourse(teacher, "A");
        UUID langA = seedClassGroup(courseA, teacher, "Lenguaje");
        // 90, 75 and 47 — a podium whose order is unmistakable, and nowhere near a tie.
        gradeOneArea(seedEnrollment(seedStudent("Ana", "Perez"), courseA), langA,
            new BigDecimal("10"), new BigDecimal("40"), new BigDecimal("35"), new BigDecimal("5"));
        gradeOneArea(seedEnrollment(seedStudent("Beto", "Quispe"), courseA), langA,
            new BigDecimal("10"), new BigDecimal("30"), new BigDecimal("30"), new BigDecimal("5"));
        gradeOneArea(seedEnrollment(seedStudent("Carla", "Rojas"), courseA), langA,
            new BigDecimal("5"), new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("2"));

        // Another classroom of the same gestión, holding the best mark in the building.
        UUID courseB = seedCourse(teacher, "B");
        UUID langB = seedClassGroup(courseB, teacher, "Lenguaje");
        gradeOneArea(seedEnrollment(seedStudent("Dario", "Soto"), courseB), langB,
            new BigDecimal("10"), new BigDecimal("45"), new BigDecimal("40"), new BigDecimal("5"));
    }

    /** One area graded in the first trimester. {@code total_score} is the database's own sum. */
    private void gradeOneArea(UUID enrollmentId, UUID classGroupId, BigDecimal being,
                              BigDecimal knowing, BigDecimal doing, BigDecimal deciding) {
        jdbc.update(
            "INSERT INTO academic_scores (id_academic_score, id_course_enrollment, id_class_group, "
                + "trimester, score_being, score_knowing, score_doing, score_deciding) "
                + "VALUES (?,?,?,1,?,?,?,?)",
            UUID.randomUUID(), enrollmentId, classGroupId, being, knowing, doing, deciding);
    }

    @Test
    void honorRoll_ordersTheCourseByTheAverageTheSheetPrintsAndCutsAtThePlacesAsked()
            throws Exception {
        String body = mvc.perform(get("/api/gradebook/honor-roll")
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("id_course", courseA.toString())
                .param("places", "2"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<List<String>>read(body, "$[*].fullName"))
            .containsExactly("Ana Perez", "Beto Quispe");
        assertThat(JsonPath.<List<Integer>>read(body, "$[*].position")).containsExactly(1, 2);
        assertThat(new BigDecimal(JsonPath.read(body, "$[0].finalAverage").toString()))
            .isEqualByComparingTo("90.00");
    }

    /**
     * The point of the whole-school scope: the best of the building, not the best of one room. The
     * top mark sits in course B, so a podium that opens with course A's best is reading one course.
     */
    @Test
    void institutionHonorRoll_reachesAcrossEveryCourseOfTheSchool() throws Exception {
        String body = mvc.perform(get("/api/gradebook/honor-roll/institution")
                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                .param("id_academic_year", String.valueOf(currentAcademicYearId()))
                .param("places", "2"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(JsonPath.<List<String>>read(body, "$[*].fullName"))
            .containsExactly("Dario Soto", "Ana Perez");
        assertThat(JsonPath.<List<String>>read(body, "$[*].parallelName"))
            .containsExactly("B", "A");
        assertThat(JsonPath.<List<Integer>>read(body, "$[*].position")).containsExactly(1, 2);
    }

    /** The school's podium is the Director's to read: a teacher owns their classroom, not the year. */
    @Test
    void institutionHonorRoll_isClosedToATeacher() throws Exception {
        mvc.perform(get("/api/gradebook/honor-roll/institution")
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher"))
                .param("id_academic_year", String.valueOf(currentAcademicYearId()))
                .param("places", "10"))
            .andExpect(status().isForbidden());
    }
}
