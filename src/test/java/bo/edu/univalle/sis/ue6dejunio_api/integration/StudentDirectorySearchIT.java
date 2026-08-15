package bo.edu.univalle.sis.ue6dejunio_api.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec: RF31 - GET /api/students/search. Blank q routes to the no-filter list query; non-blank
 * q routes to the LIKE query; grade/parallel/level are derived from the active (Effective)
 * course enrollment; Teacher is auto-scoped to their own homeroom course and never gets 403.
 */
class StudentDirectorySearchIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private UUID otherTeacher;
    private UUID homeroomCourse;
    private UUID otherCourse;
    private UUID studentInHomeroom;
    private UUID studentInOtherCourse;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        otherTeacher = seedUser("Teacher", false);
        homeroomCourse = seedCourse(teacher, "A");
        otherCourse = seedCourse(otherTeacher, "B");

        studentInHomeroom = seedNamedStudent("Maria", "Lopez", "1234567890123");
        studentInOtherCourse = seedNamedStudent("Juan", "Perez", "9999999999999");

        seedEnrollment(studentInHomeroom, homeroomCourse);
        seedEnrollment(studentInOtherCourse, otherCourse);
    }

    private UUID seedNamedStudent(String names, String lastNames, String rudeCode) {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        jdbc.update(
            "INSERT INTO students (id_student, rude_code, identity_card, names, last_names, "
                + "birth_date, gender, status) VALUES (?,?,?,?,?,?,?,?)",
            id, rudeCode, "ID-" + suffix, names, lastNames,
            java.sql.Date.valueOf("2015-01-01"), "F", "Effective");
        return id;
    }

    @Test
    void blankQ_director_returnsAllActiveStudents() throws Exception {
        mvc.perform(get("/api/students/search")
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void searchByPartialName_director_matchesCaseInsensitive() throws Exception {
        mvc.perform(get("/api/students/search").param("q", "lopez")
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(studentInHomeroom.toString()));
    }

    @Test
    void searchByRudeCode_director_matches() throws Exception {
        mvc.perform(get("/api/students/search").param("q", "1234567890123")
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].rudeCode").value("1234567890123"));
    }

    @Test
    void gradeParallelLevel_derivedFromActiveEnrollment() throws Exception {
        mvc.perform(get("/api/students/search").param("q", "Lopez")
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].grade").isNotEmpty())
            .andExpect(jsonPath("$.content[0].parallel").value("A"));
    }

    @Test
    void teacher_scopedToOwnHomeroomCourse_othersExcluded() throws Exception {
        mvc.perform(get("/api/students/search")
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(studentInHomeroom.toString()));
    }

    @Test
    void teacher_searchForStudentOutsideOwnCourse_emptyNeverOtherCourseData() throws Exception {
        mvc.perform(get("/api/students/search").param("q", "Perez")
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0));
    }
}
