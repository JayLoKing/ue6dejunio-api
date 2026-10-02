package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * "The current year" is the active one, not the newest. 2026 is active in the seed; a 2027 row
 * created while 2026 is still running must not pull any default onto itself. Each test first
 * creates that 2027 row, which is the exact event that used to move the whole system.
 * (AbstractIntegrationTest removes it again before the next test.)
 */
class ActiveAcademicYearResolutionIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private Integer activeYearId;
    private Integer newerYearId;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        activeYearId = academicYearId(2026);
        jdbc.update("INSERT INTO academic_years (year) VALUES (2027)");
        newerYearId = academicYearId(2027);
    }

    @Test
    void trimesters_omittedYear_stayOnTheActiveYearWhenANewerOneExists() throws Exception {
        String body =
                mvc.perform(
                                get("/api/catalog/trimesters")
                                        .header(
                                                "Authorization",
                                                "Bearer " + tokenFor(director, "Director")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // 2027 has no periods configured: an empty list would mean the default followed max(year).
        JsonNode arr = json.readTree(body);
        assertThat(arr).hasSize(3);
    }

    @Test
    void postCourse_landsInTheActiveYearWhenANewerOneExists() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        Integer gradeId = jdbc.queryForObject("SELECT id_grade FROM grades LIMIT 1", Integer.class);
        Integer parallelId =
                jdbc.queryForObject(
                        "SELECT id_parallel FROM parallels WHERE name = 'A'", Integer.class);
        UUID subjectId = jdbc.queryForObject("SELECT id_subject FROM subjects LIMIT 1", UUID.class);
        String body =
                json.writeValueAsString(
                        Map.of(
                                "id_grade",
                                gradeId,
                                "id_parallel",
                                parallelId,
                                "assignments",
                                List.of(
                                        Map.of(
                                                "id_subject",
                                                subjectId.toString(),
                                                "id_teacher",
                                                teacher.toString()))));

        mvc.perform(
                        post("/api/courses")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        List<Integer> years =
                jdbc.queryForList("SELECT id_academic_year FROM courses", Integer.class);
        assertThat(years).containsExactly(activeYearId);
        assertThat(years).doesNotContain(newerYearId);
    }

    @Test
    void studentSearch_defaultsToTheActiveYearWhenANewerOneExists() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        UUID inActive = seedStudent("Maria", "Lopez");
        UUID inNewer = seedStudent("Juan", "Perez");
        seedEnrollment(inActive, seedCourse(teacher, "A", activeYearId));
        seedEnrollment(inNewer, seedCourse(teacher, "B", newerYearId));

        mvc.perform(
                        get("/api/students/search")
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(inActive.toString()));
    }
}
