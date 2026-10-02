package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The Director picks the working year; nobody else does, and a closed year cannot be picked. */
class AcademicYearActivationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private String bearer(String role) {
        return "Bearer " + tokenFor(seedUser(role, false), role);
    }

    private Integer activeYear() {
        return jdbc.queryForObject("SELECT year FROM academic_years WHERE active", Integer.class);
    }

    @Test
    void director_activates2025_catalogFlagsMoveAndNewCoursesFollow() throws Exception {
        Integer id2025 = academicYearId(2025);

        mvc.perform(
                        put("/api/academic-years/" + id2025 + "/activate")
                                .header("Authorization", bearer("Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.closed").value(false));

        mvc.perform(get("/api/catalog/academic-years").header("Authorization", bearer("Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].year").value(2026))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].closed").value(false))
                .andExpect(jsonPath("$[1].year").value(2025))
                .andExpect(jsonPath("$[1].active").value(true));

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
                                .header("Authorization", bearer("Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT id_academic_year FROM courses", Integer.class))
                .containsExactly(id2025);
    }

    @Test
    void activatingTheActiveYear_isANoOp() throws Exception {
        Integer id2026 = academicYearId(2026);

        mvc.perform(
                        put("/api/academic-years/" + id2026 + "/activate")
                                .header("Authorization", bearer("Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        assertThat(activeYear()).isEqualTo(2026);
    }

    @Test
    void closedYear_isRefusedWith409AndTheActiveYearStays() throws Exception {
        Integer id2025 = academicYearId(2025);
        jdbc.update(
                "UPDATE academic_years SET closed_at = now() WHERE id_academic_year = ?", id2025);

        mvc.perform(
                        put("/api/academic-years/" + id2025 + "/activate")
                                .header("Authorization", bearer("Director")))
                .andExpect(status().isConflict());

        assertThat(activeYear()).isEqualTo(2026);
    }

    @Test
    void unknownYear_is404() throws Exception {
        mvc.perform(
                        put("/api/academic-years/999999/activate")
                                .header("Authorization", bearer("Director")))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherAndSecretary_forbidden_anonymous_unauthorized() throws Exception {
        String url = "/api/academic-years/" + academicYearId(2025) + "/activate";

        mvc.perform(put(url).header("Authorization", bearer("Teacher")))
                .andExpect(status().isForbidden());
        mvc.perform(put(url).header("Authorization", bearer("Secretary")))
                .andExpect(status().isForbidden());
        mvc.perform(put(url)).andExpect(status().isUnauthorized());

        assertThat(activeYear()).isEqualTo(2026);
    }
}
