package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Records are independent per gestión, and a teacher works in the active one only. Teachers rotate
 * courses every year, so the same person is homeroom of "Primero A" in a closed gestión and again
 * in the active one: everything a teacher reads must come from the active gestión, never from the
 * one that was closed. Only the Director may look back, and only by asking for that year.
 */
class TeacherActiveYearScopeIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Secreta123";

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;

    private Integer closedYear;
    private Integer activeYear;

    @BeforeEach
    void closeThePreviousGestion() {
        jdbc.update("UPDATE academic_years SET closed_at = now() WHERE year = 2025");
        closedYear = academicYearId(2025);
        activeYear = currentAcademicYearId();
    }

    private UUID seedPlan(UUID courseId, UUID teacher, int planNumber, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO curriculum_plans (id_curriculum_plan, id_course, plan_number, "
                        + "trimester, period_start, period_end, status, created_by) "
                        + "VALUES (?,?,?,1,DATE '2026-02-01',DATE '2026-02-28',?,?)",
                id,
                courseId,
                planNumber,
                status,
                teacher);
        return id;
    }

    private String emailOf(UUID user) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id_user = ?", String.class, user);
    }

    @Test
    void aTeachersClassGroups_areOnlyThoseOfTheActiveGestion() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        UUID before = seedCourse(teacher, "A", closedYear);
        UUID now = seedCourse(teacher, "A", activeYear);
        seedClassGroup(before, teacher, "Matematicas");
        UUID current = seedClassGroup(now, teacher, "Matematicas");

        mvc.perform(
                        get("/api/teachers/{id}/class-groups", teacher)
                                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(current.toString()))
                .andExpect(jsonPath("$[0].courseId").value(now.toString()));
    }

    @Test
    void aTeacherWhoseOnlyClassroomIsInAClosedGestion_logsInWithoutOne() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        jdbc.update(
                "UPDATE users SET password = ? WHERE id_user = ?",
                passwordEncoder.encode(PASSWORD),
                teacher);
        seedCourse(teacher, "A", closedYear);

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\""
                                                + emailOf(teacher)
                                                + "\",\"password\":\""
                                                + PASSWORD
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").doesNotExist());
    }

    @Test
    void aTeacherWithAClassroomInEachGestion_logsInWithTheActiveOne() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        jdbc.update(
                "UPDATE users SET password = ? WHERE id_user = ?",
                passwordEncoder.encode(PASSWORD),
                teacher);
        seedCourse(teacher, "A", closedYear);
        UUID now = seedCourse(teacher, "A", activeYear);

        mvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\""
                                                + emailOf(teacher)
                                                + "\",\"password\":\""
                                                + PASSWORD
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(now.toString()));
    }

    @Test
    void aTeachersHomeroomStudents_neverComeFromAClosedGestion() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        UUID before = seedCourse(teacher, "A", closedYear);
        seedEnrollment(seedStudent(), before);

        mvc.perform(
                        get("/api/teachers/{id}/students", teacher)
                                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void aTeachersPlans_areOnlyThoseOfTheActiveGestion_whateverYearTheyAskFor() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        UUID before = seedCourse(teacher, "A", closedYear);
        UUID now = seedCourse(teacher, "A", activeYear);
        seedPlan(before, teacher, 1, "Approved");
        UUID current = seedPlan(now, teacher, 1, "Draft");
        String token = tokenFor(teacher, "Teacher");

        mvc.perform(get("/api/pdc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(current.toString()));

        mvc.perform(
                        get("/api/pdc")
                                .param("academicYearId", closedYear.toString())
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(current.toString()));
    }

    @Test
    void theDirector_readsTheActiveGestionByDefault_andAPastOneWhenAsked() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        UUID director = seedUser("Director", false);
        UUID before = seedCourse(teacher, "A", closedYear);
        UUID now = seedCourse(teacher, "A", activeYear);
        UUID past1 = seedPlan(before, teacher, 1, "Approved");
        UUID past2 = seedPlan(before, teacher, 2, "Published");
        UUID current = seedPlan(now, teacher, 1, "Published");
        String token = tokenFor(director, "Director");

        mvc.perform(get("/api/pdc").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(current.toString()));

        mvc.perform(
                        get("/api/pdc")
                                .param("academicYearId", closedYear.toString())
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[*].id")
                                .value(containsInAnyOrder(past1.toString(), past2.toString())));
    }
}
