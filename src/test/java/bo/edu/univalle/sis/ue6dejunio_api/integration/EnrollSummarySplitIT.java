package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * What a roster load says about the students it did not create: who comes from last year's roll and
 * who is coming back after a gap.
 *
 * <p>Driven through the endpoint over a real Postgres because the split is one batched query about
 * the year before the course's own, and a mocked port would only prove the loop reads its answer.
 */
class EnrollSummarySplitIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private UUID director;
    private UUID teacher;
    private UUID course2026;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        course2026 = seedCourse(teacher, "A");
    }

    private UUID seatIn(UUID student, int year, String parallel, String status) {
        Integer yearId = academicYearId(year);
        // One course per (grade, parallel, gestión): later students of the same year share it.
        UUID course =
                jdbc
                        .query(
                                "SELECT c.id_course FROM courses c JOIN parallels p "
                                        + "ON p.id_parallel = c.id_parallel "
                                        + "WHERE c.id_academic_year = ? AND p.name = ?",
                                (rs, i) -> rs.getObject(1, UUID.class),
                                yearId,
                                parallel)
                        .stream()
                        .findFirst()
                        .orElseGet(() -> seedCourse(seedUser("Teacher", false), parallel, yearId));
        UUID enrolment = seedEnrollment(student, course);
        jdbc.update(
                "UPDATE course_enrollments SET status = ? WHERE id_course_enrollment = ?",
                status,
                enrolment);
        return course;
    }

    private String rowOf(UUID student) {
        return jdbc.queryForObject(
                "SELECT '{\"rudeCode\": \"' || rude_code || '\", \"identityCard\": \"' || identity_card"
                        + " || '\", \"names\": \"' || names || '\", \"lastNames\": \"' || last_names"
                        + " || '\", \"birthDate\": \"2015-01-01\", \"gender\": \"M\"}' "
                        + "FROM students WHERE id_student = ?",
                String.class,
                student);
    }

    private static String newcomer() {
        return """
            {"rudeCode": "NEW-RUDE-1", "identityCard": "NEW-CI-1", "names": "Nuevo",
             "lastNames": "Alumno", "birthDate": "2015-01-01", "gender": "M"}
            """;
    }

    private ResultActions load(String... rows) throws Exception {
        return mvc.perform(
                        post("/api/course-enrollments/sync")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType("application/json")
                                .content(
                                        """
                                        {"id_course": "%s", "students": [%s]}
                                        """
                                                .formatted(course2026, String.join(",", rows))))
                .andExpect(status().isOk());
    }

    private void ensureYear(int year) {
        jdbc.update("INSERT INTO academic_years (year) VALUES (?)", year);
    }

    /** One load mixing every kind of student the summary tells apart. */
    @Test
    void mixedLoad_splitsNewFromPreviousYearReenteringAndWithdrawn() throws Exception {
        ensureYear(2024);
        UUID continuing = seedStudent("Ana", "Continua");
        seatIn(continuing, 2025, "B", "Effective");
        UUID returning = seedStudent("Beto", "Regresa");
        seatIn(returning, 2024, "B", "Effective");
        UUID leftLastYear = seedStudent("Carla", "Retirada");
        seatIn(leftLastYear, 2025, "B", "Withdrawn");
        UUID gone = seedStudent("Dino", "Baja");
        seatIn(gone, 2025, "B", "Effective");
        jdbc.update("UPDATE students SET status = 'Withdrawn' WHERE id_student = ?", gone);

        load(rowOf(continuing), rowOf(returning), rowOf(leftLastYear), rowOf(gone), newcomer())
                .andExpect(jsonPath("$.studentsTotal").value(5))
                .andExpect(jsonPath("$.studentsCreated").value(1))
                .andExpect(jsonPath("$.studentsFromPreviousYear").value(1))
                .andExpect(jsonPath("$.studentsReentering").value(2))
                .andExpect(jsonPath("$.skippedWithdrawn.length()").value(1));
    }

    /** Distinct students, however many rows the file spends on them. */
    @Test
    void repeatedRows_areCountedOnceInEveryGroup() throws Exception {
        UUID continuing = seedStudent("Ana", "Continua");
        seatIn(continuing, 2025, "B", "Effective");

        load(rowOf(continuing), rowOf(continuing), newcomer(), newcomer())
                .andExpect(jsonPath("$.studentsCreated").value(1))
                .andExpect(jsonPath("$.studentsFromPreviousYear").value(1))
                .andExpect(jsonPath("$.studentsReentering").value(0));
    }

    /** The question is about the year before the course's, not before the active one. */
    @Test
    void theSplitIsRelativeToTheYearOfTheCourseBeingLoaded() throws Exception {
        UUID student = seedStudent("Ana", "Continua");
        seatIn(student, 2025, "B", "Effective");
        ensureYear(2024);
        ensureYear(2023);
        // A course of 2024 rather than of 2025: a second course of the year she already sits in
        // would leave her out of the load altogether, which is another rule's business.
        UUID course2024 = seedCourse(teacher, "C", academicYearId(2024));

        // Loading 2024's course: the previous year is 2023, where she was never enrolled, even
        // though she sat in 2025, the year before the active one.
        mvc.perform(
                        post("/api/course-enrollments/sync")
                                .header("Authorization", "Bearer " + tokenFor(director, "Director"))
                                .contentType("application/json")
                                .content(
                                        """
                                        {"id_course": "%s", "students": [%s]}
                                        """
                                                .formatted(course2024, rowOf(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsFromPreviousYear").value(0))
                .andExpect(jsonPath("$.studentsReentering").value(1));
    }

    /** No earlier gestión on record: everyone already on file is re-entering. */
    @Test
    void noPreviousYear_everyExistingStudentIsReentering() throws Exception {
        // 2025 is the earliest gestión the schema seeds, so a course there has nothing before it.
        course2026 = seedCourse(teacher, "C", academicYearId(2025));
        UUID existing = seedStudent("Ana", "Existente");

        load(rowOf(existing), newcomer())
                .andExpect(jsonPath("$.studentsCreated").value(1))
                .andExpect(jsonPath("$.studentsFromPreviousYear").value(0))
                .andExpect(jsonPath("$.studentsReentering").value(1));
    }
}
