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
import org.springframework.test.web.servlet.ResultActions;

/**
 * The rest of the academic writes behind a closed gestión: plans and their progress, adaptations,
 * the pedagogical report, enrolments, courses and their class groups, trimester periods and the
 * risk follow-up. {@link ClosedYearWritesIT} covers the grading core.
 *
 * <p>Every refusal is asserted by its message and not only by its status: several of these use
 * cases can answer 409 for their own reasons, and a test that only read the status would stay green
 * for the wrong one.
 */
class ClosedYearWritesRestIT extends AbstractIntegrationTest {

    private static final String CLOSED_2026 = "La gestión 2026 está cerrada; no admite cambios.";

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private UUID otherTeacher;
    private UUID course;
    private UUID classGroup;
    private UUID student;
    private UUID enrollment;
    private UUID plan;
    private UUID planSubject;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        otherTeacher = seedUser("Teacher", false);
        course = seedCourse(teacher, "A");
        classGroup = seedClassGroup(course, teacher, "Lenguaje");
        student = seedStudent();
        enrollment = seedEnrollment(student, course);
        plan = seedPlan(course, 1, "Draft");
        planSubject = seedPlanSubject(plan, classGroup);
    }

    // ---- fixtures ----

    private UUID seedPlan(UUID courseId, int planNumber, String status) {
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

    private UUID seedPlanSubject(UUID planId, UUID classGroupId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO curriculum_plan_subjects (id_plan_subject, id_curriculum_plan, "
                        + "id_class_group) VALUES (?,?,?)",
                id,
                planId,
                classGroupId);
        return id;
    }

    private UUID seedAdaptation() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO curriculum_adaptations (id_curriculum_adaptation, "
                        + "id_curriculum_plan, id_student, condition_type) VALUES (?,?,?,?)",
                id,
                plan,
                student,
                "Visual");
        return id;
    }

    private UUID seedPrediction() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO risk_predictions (id_risk_prediction, id_student, id_class_group, "
                        + "trimester, risk_level, p_fail, p_outstanding) "
                        + "VALUES (?,?,?,1,'EnRiesgo',0.6,0.1)",
                id,
                student,
                classGroup);
        return id;
    }

    private void closeYear() {
        jdbc.update(
                "UPDATE academic_years SET closed_at = now(), active = false WHERE year = 2026");
    }

    private String as(UUID user, String role) {
        return "Bearer " + tokenFor(user, role);
    }

    private ResultActions send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            UUID user,
            String role,
            String body)
            throws Exception {
        var builder = request.header("Authorization", as(user, role));
        if (body != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(builder);
    }

    private static void refused(ResultActions result) throws Exception {
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(CLOSED_2026));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    // ---- PDC and progress ----

    @Test
    void closedYear_rejectsOpeningAPlan() throws Exception {
        closeYear();

        refused(
                send(
                        post("/api/pdc"),
                        teacher,
                        "Teacher",
                        """
                        {"id_course":"%s","plan_number":2,"trimester":1,
                         "period_start":"2026-03-01","period_end":"2026-03-31"}
                        """
                                .formatted(course)));

        assertThat(count("SELECT COUNT(*) FROM curriculum_plans")).isEqualTo(1);
    }

    @Test
    void closedYear_rejectsEditingPublishingAndDeletingAPlan() throws Exception {
        closeYear();

        refused(
                send(
                        put("/api/pdc/" + plan),
                        teacher,
                        "Teacher",
                        "{\"holisticObjective\":\"Otro\"}"));
        refused(send(post("/api/pdc/" + plan + "/publish"), teacher, "Teacher", null));
        refused(send(delete("/api/pdc/" + plan), teacher, "Teacher", null));

        assertThat(count("SELECT COUNT(*) FROM curriculum_plans WHERE status = 'Draft'"))
                .isEqualTo(1);
    }

    @Test
    void closedYear_rejectsReviewingAPlan() throws Exception {
        UUID published = seedPlan(course, 2, "Published");
        closeYear();

        refused(send(post("/api/pdc/" + published + "/approve"), director, "Director", null));
        refused(
                send(
                        post("/api/pdc/" + published + "/observe"),
                        director,
                        "Director",
                        "{\"observations\":\"Corregir\"}"));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM curriculum_plans WHERE id_curriculum_plan = ?",
                                String.class,
                                published))
                .isEqualTo("Published");
    }

    @Test
    void closedYear_rejectsWritingASubjectBlock() throws Exception {
        closeYear();

        refused(
                send(
                        put("/api/pdc/" + plan + "/subjects/" + planSubject),
                        teacher,
                        "Teacher",
                        "{\"learningObjective\":\"Leer\"}"));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT learning_objective FROM curriculum_plan_subjects "
                                        + "WHERE id_plan_subject = ?",
                                String.class,
                                planSubject))
                .isNull();
    }

    @Test
    void closedYear_rejectsRecordingProgress() throws Exception {
        closeYear();

        refused(
                send(
                        post("/api/pdc/" + plan + "/progress"),
                        teacher,
                        "Teacher",
                        "{\"advancedContent\":\"Tema 1\",\"percentage\":10}"));

        assertThat(count("SELECT COUNT(*) FROM curriculum_plan_progress")).isZero();
    }

    @Test
    void closedYear_stillServesPlanReads_andReopeningLetsAPlanEditThrough() throws Exception {
        closeYear();

        mvc.perform(get("/api/pdc/" + plan).header("Authorization", as(teacher, "Teacher")))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/pdc/" + plan + "/progress")
                                .header("Authorization", as(teacher, "Teacher")))
                .andExpect(status().isOk());

        mvc.perform(
                        put("/api/academic-years/" + academicYearId(2026) + "/reopen")
                                .header("Authorization", as(director, "Director")))
                .andExpect(status().isOk());

        send(put("/api/pdc/" + plan), teacher, "Teacher", "{\"holisticObjective\":\"Otro\"}")
                .andExpect(status().isOk());
    }

    // ---- adaptations ----

    @Test
    void closedYear_rejectsCreatingAnAdaptation() throws Exception {
        closeYear();

        refused(
                send(
                        post("/api/adaptations"),
                        teacher,
                        "Teacher",
                        "{\"id_curriculum_plan\":\"%s\",\"id_student\":\"%s\",\"conditionType\":\"Visual\"}"
                                .formatted(plan, student)));

        assertThat(count("SELECT COUNT(*) FROM curriculum_adaptations")).isZero();
    }

    @Test
    void closedYear_rejectsUpdatingAndDeletingAnAdaptation() throws Exception {
        UUID adaptation = seedAdaptation();
        closeYear();

        refused(
                send(
                        put("/api/adaptations/" + adaptation),
                        director,
                        "Director",
                        "{\"conditionType\":\"Auditiva\"}"));
        refused(send(delete("/api/adaptations/" + adaptation), director, "Director", null));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT condition_type FROM curriculum_adaptations", String.class))
                .isEqualTo("Visual");
    }

    // ---- pedagogical report ----

    @Test
    void closedYear_rejectsSavingThePedagogicalReport() throws Exception {
        closeYear();

        refused(
                send(
                        put(
                                "/api/gradebook/pedagogical-report?id_course="
                                        + course
                                        + "&trimester=1"),
                        teacher,
                        "Teacher",
                        "{\"achievements\":\"Logros\"}"));

        assertThat(count("SELECT COUNT(*) FROM pedagogical_reports")).isZero();
    }

    // ---- enrolments ----

    @Test
    void closedYear_rejectsEnrollingAStudent_singleAndSync() throws Exception {
        closeYear();
        String one =
                """
                {"rudeCode":"R-1","identityCard":"1234567","names":"Ana","lastNames":"Quispe",
                 "birthDate":"2012-03-04","gender":"F"}
                """;

        refused(
                send(
                        post("/api/course-enrollments"),
                        director,
                        "Director",
                        "{\"id_course\":\"%s\",\"student\":%s}".formatted(course, one)));
        refused(
                send(
                        post("/api/course-enrollments/sync"),
                        director,
                        "Director",
                        "{\"id_course\":\"%s\",\"students\":[%s]}".formatted(course, one)));

        assertThat(count("SELECT COUNT(*) FROM students")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM course_enrollments")).isEqualTo(1);
    }

    // ---- courses and class groups ----

    @Test
    void closedYear_rejectsUpdatingAndDeletingACourse() throws Exception {
        closeYear();

        refused(send(put("/api/courses/" + course), director, "Director", "{\"active\":false}"));
        refused(send(delete("/api/courses/" + course), director, "Director", null));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM courses WHERE id_course = ?",
                                Boolean.class,
                                course))
                .isTrue();
    }

    @Test
    void closedYear_rejectsChangingTheHomeroomTeacher() throws Exception {
        closeYear();

        refused(
                send(
                        put("/api/courses/" + course + "/homeroom-teacher"),
                        director,
                        "Director",
                        "{\"id_homeroom_teacher\":\"%s\"}".formatted(otherTeacher)));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT id_homeroom_teacher FROM courses WHERE id_course = ?",
                                UUID.class,
                                course))
                .isEqualTo(teacher);
    }

    @Test
    void closedYear_rejectsSwappingHomeroomTeachers_whenEitherCourseIsClosed() throws Exception {
        UUID course2025 = seedCourse(otherTeacher, "B", academicYearId(2025));
        closeYear();

        refused(
                send(
                        put("/api/courses/homeroom-teachers/swap"),
                        director,
                        "Director",
                        "{\"id_course_a\":\"%s\",\"id_course_b\":\"%s\"}"
                                .formatted(course2025, course)));
    }

    @Test
    void closedYear_rejectsReassigningAClassGroupTeacher() throws Exception {
        closeYear();

        refused(
                send(
                        put("/api/courses/" + course + "/class-groups/" + classGroup + "/teacher"),
                        director,
                        "Director",
                        "{\"teacherId\":\"%s\"}".formatted(otherTeacher)));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT id_teacher FROM class_groups WHERE id_class_group = ?",
                                UUID.class,
                                classGroup))
                .isEqualTo(teacher);
    }

    @Test
    void closedYear_stillServesCourseReads() throws Exception {
        closeYear();

        mvc.perform(get("/api/courses/" + course).header("Authorization", as(director, "Director")))
                .andExpect(status().isOk());
    }

    // ---- trimester periods ----

    @Test
    void closedYear_rejectsCreatingAndEditingAndDeletingAPeriod() throws Exception {
        UUID t1 =
                jdbc.queryForObject(
                        "SELECT id_academic_trimester FROM academic_trimesters "
                                + "WHERE id_academic_year = ? AND trimester = 1",
                        UUID.class,
                        academicYearId(2026));
        jdbc.update(
                "DELETE FROM academic_trimesters WHERE id_academic_year = ? AND trimester = 3",
                academicYearId(2026));
        closeYear();

        refused(
                send(
                        post("/api/trimester-periods"),
                        director,
                        "Director",
                        "{\"id_academic_year\":%d,\"trimester\":3,\"start_date\":\"2026-09-01\",\"end_date\":\"2026-11-30\"}"
                                .formatted(academicYearId(2026))));
        refused(
                send(
                        put("/api/trimester-periods/" + t1),
                        director,
                        "Director",
                        "{\"start_date\":\"2026-02-02\",\"end_date\":\"2026-05-30\"}"));
        refused(send(delete("/api/trimester-periods/" + t1), director, "Director", null));

        assertThat(
                        count(
                                "SELECT COUNT(*) FROM academic_trimesters WHERE id_academic_year = ?",
                                academicYearId(2026)))
                .isEqualTo(2);
    }

    // ---- risk follow-up ----

    @Test
    void closedYear_rejectsMarkingAPredictionAttended() throws Exception {
        UUID prediction = seedPrediction();
        closeYear();

        refused(
                send(
                        put("/api/risk-predictions/" + prediction + "/attend?attended=true"),
                        director,
                        "Director",
                        null));

        assertThat(jdbc.queryForObject("SELECT is_attended FROM risk_predictions", Boolean.class))
                .isFalse();
    }

    // ---- withdrawal leaves a closed year's history alone ----

    @Test
    void withdrawing_aStudentEffectiveInAClosedAndAnOpenYear_touchesOnlyTheOpenOne()
            throws Exception {
        UUID course2025 = seedCourse(otherTeacher, "B", academicYearId(2025));
        UUID enrollment2025 = seedEnrollment(student, course2025);
        jdbc.update(
                "UPDATE academic_years SET closed_at = now(), active = false WHERE year = 2025");

        send(
                        post("/api/students/" + student + "/withdraw"),
                        director,
                        "Director",
                        "{\"reason\":\"Retiro Voluntario\"}")
                .andExpect(status().isNoContent());

        assertThat(statusOf(enrollment)).isEqualTo("Withdrawn");
        assertThat(statusOf(enrollment2025)).isEqualTo("Effective");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM students WHERE id_student = ?",
                                String.class,
                                student))
                .isEqualTo("Withdrawn");
    }

    private String statusOf(UUID courseEnrollmentId) {
        return jdbc.queryForObject(
                "SELECT status FROM course_enrollments WHERE id_course_enrollment = ?",
                String.class,
                courseEnrollmentId);
    }
}
