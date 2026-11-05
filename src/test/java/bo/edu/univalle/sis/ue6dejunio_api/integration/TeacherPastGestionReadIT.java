package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Read authorization by direct URL. A teacher who ran a classroom in a closed gestión still holds
 * its ids — in a bookmark, in the browser history — but works in the active gestión only, so
 * opening last year's class group, course or plan is refused. The Director keeps reading past
 * gestiones.
 */
class TeacherPastGestionReadIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID teacher;
    private UUID director;
    private UUID pastCourse;
    private UUID currentCourse;
    private UUID pastGroup;
    private UUID currentGroup;

    @BeforeEach
    void aTeacherWithAClassroomInEachGestion() {
        jdbc.update("UPDATE academic_years SET closed_at = now() WHERE year = 2025");
        teacher = seedUser("Teacher", false);
        director = seedUser("Director", false);
        pastCourse = seedCourse(teacher, "A", academicYearId(2025));
        currentCourse = seedCourse(teacher, "A", currentAcademicYearId());
        pastGroup = seedClassGroup(pastCourse, teacher, "Matematicas");
        currentGroup = seedClassGroup(currentCourse, teacher, "Matematicas");
    }

    private UUID seedPlan(UUID courseId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO curriculum_plans (id_curriculum_plan, id_course, plan_number, "
                        + "trimester, period_start, period_end, status, created_by) "
                        + "VALUES (?,?,1,1,DATE '2026-02-01',DATE '2026-02-28','Approved',?)",
                id,
                courseId,
                teacher);
        return id;
    }

    private ResultActions criteriaOf(UUID classGroupId, UUID caller, String role) throws Exception {
        return mvc.perform(
                get("/api/criteria")
                        .param("id_class_group", classGroupId.toString())
                        .param("trimester", "1")
                        .header("Authorization", "Bearer " + tokenFor(caller, role)));
    }

    private ResultActions overviewOf(UUID courseId, UUID caller, String role) throws Exception {
        return mvc.perform(
                get("/api/courses/{id}/overview", courseId)
                        .param("trimester", "1")
                        .header("Authorization", "Bearer " + tokenFor(caller, role)));
    }

    private ResultActions plan(UUID planId, UUID caller, String role) throws Exception {
        return mvc.perform(
                get("/api/pdc/{id}", planId)
                        .header("Authorization", "Bearer " + tokenFor(caller, role)));
    }

    @Test
    void aClassGroup_isReadByItsTeacherOnlyInTheActiveGestion() throws Exception {
        criteriaOf(pastGroup, teacher, "Teacher").andExpect(status().isForbidden());
        criteriaOf(currentGroup, teacher, "Teacher").andExpect(status().isOk());
    }

    @Test
    void aClassGroupOfAClosedGestion_isStillReadByTheDirector() throws Exception {
        criteriaOf(pastGroup, director, "Director").andExpect(status().isOk());
    }

    @Test
    void aCourse_isReadByItsHomeroomTeacherOnlyInTheActiveGestion() throws Exception {
        overviewOf(pastCourse, teacher, "Teacher").andExpect(status().isForbidden());
        overviewOf(currentCourse, teacher, "Teacher").andExpect(status().isOk());
    }

    @Test
    void aCourseOfAClosedGestion_isStillReadByTheDirector() throws Exception {
        overviewOf(pastCourse, director, "Director").andExpect(status().isOk());
    }

    @Test
    void aPlan_isReadByItsTeacherOnlyInTheActiveGestion_andByTheDirectorAlways() throws Exception {
        UUID past = seedPlan(pastCourse);
        UUID current = seedPlan(currentCourse);

        plan(past, teacher, "Teacher").andExpect(status().isForbidden());
        plan(current, teacher, "Teacher").andExpect(status().isOk());
        plan(past, director, "Director").andExpect(status().isOk());
    }
}
