package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Closing a year freezes the libreta result on every enrollment, over a real Postgres.
 *
 * <p>The decisive case is the one between 50.50 and 50.99: the libreta prints 51, so the student is
 * APPROVED although the stored average is below the passing mark. The frozen average is compared
 * with the one the report card endpoint returns for the same student, so the two cannot drift.
 */
class YearClosingFreezeIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private UUID director;
    private UUID teacher;
    private UUID course;
    private UUID classGroup;
    private UUID approved;
    private UUID roundedUp;
    private UUID failed;
    private UUID withdrawn;
    private UUID ungraded;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
        course = seedCourse(teacher, "A", academicYearId(2026));
        classGroup = seedClassGroup(course, teacher, "Lenguaje");

        approved = seedEnrollment(seedStudent("Ana", "Alvarez"), course);
        roundedUp = seedEnrollment(seedStudent("Beto", "Bernal"), course);
        failed = seedEnrollment(seedStudent("Cleo", "Cortez"), course);
        withdrawn = seedEnrollment(seedStudent("Dino", "Duran"), course);
        ungraded = seedEnrollment(seedStudent("Eva", "Estrada"), course);

        // total = being 10 + knowing 20 + doing (total - 35) + deciding 5.
        grade(approved, 60, 60, 60); // 60.00
        grade(roundedUp, 50, 51, 51); // 152 / 3 = 50.67, prints 51
        grade(failed, 50, 50, 51); // 151 / 3 = 50.33, prints 50
        grade(withdrawn, 70, 70, 70);
        jdbc.update(
                "UPDATE course_enrollments SET status = 'Withdrawn' WHERE id_course_enrollment = ?",
                withdrawn);
    }

    private void grade(UUID enrollment, int t1, int t2, int t3) {
        int[] totals = {t1, t2, t3};
        for (int i = 0; i < 3; i++) {
            jdbc.update(
                    "INSERT INTO academic_scores (id_academic_score, id_course_enrollment, "
                            + "id_class_group, trimester, score_being, score_knowing, score_doing, "
                            + "score_deciding) VALUES (?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    enrollment,
                    classGroup,
                    i + 1,
                    new BigDecimal("10"),
                    new BigDecimal("20"),
                    new BigDecimal(totals[i] - 35),
                    new BigDecimal("5"));
        }
    }

    private String bearer() {
        return "Bearer " + tokenFor(director, "Director");
    }

    private void close() throws Exception {
        mvc.perform(
                        put("/api/academic-years/" + academicYearId(2026) + "/close")
                                .header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    private Map<String, Object> row(UUID enrollment) {
        return jdbc.queryForMap(
                "SELECT final_average, final_result, closed_at FROM course_enrollments "
                        + "WHERE id_course_enrollment = ?",
                enrollment);
    }

    private BigDecimal reportCardAverage(UUID enrollment) throws Exception {
        String body =
                mvc.perform(
                                get("/api/gradebook/report-card")
                                        .param("id_course_enrollment", enrollment.toString())
                                        .header("Authorization", bearer()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        return new BigDecimal(String.valueOf((Object) JsonPath.read(body, "$.finalAverage")));
    }

    @Test
    void close_freezesAverageAndResultOfEveryEffectiveEnrollment() throws Exception {
        close();

        assertThat((BigDecimal) row(approved).get("final_average")).isEqualByComparingTo("60.00");
        assertThat(row(approved).get("final_result")).isEqualTo("APPROVED");
        // 50.67 prints 51: approved although the average is below the passing mark.
        assertThat((BigDecimal) row(roundedUp).get("final_average")).isEqualByComparingTo("50.67");
        assertThat(row(roundedUp).get("final_result")).isEqualTo("APPROVED");
        assertThat((BigDecimal) row(failed).get("final_average")).isEqualByComparingTo("50.33");
        assertThat(row(failed).get("final_result")).isEqualTo("FAILED");
    }

    @Test
    void close_frozenAverage_equalsWhatTheReportCardReturns() throws Exception {
        close();

        for (UUID enrollment : new UUID[] {approved, roundedUp, failed}) {
            assertThat((BigDecimal) row(enrollment).get("final_average"))
                    .isEqualByComparingTo(reportCardAverage(enrollment));
        }
    }

    @Test
    void close_withdrawnAndUngraded_haveNoAverageNorResult_butEveryEnrollmentIsClosed()
            throws Exception {
        close();

        assertThat(row(withdrawn).get("final_average")).isNull();
        assertThat(row(withdrawn).get("final_result")).isNull();
        assertThat(row(ungraded).get("final_average")).isNull();
        assertThat(row(ungraded).get("final_result")).isNull();
        Timestamp yearClosedAt =
                jdbc.queryForObject(
                        "SELECT closed_at FROM academic_years WHERE year = 2026", Timestamp.class);
        assertThat(yearClosedAt).isNotNull();
        for (UUID enrollment : new UUID[] {approved, roundedUp, failed, withdrawn, ungraded}) {
            assertThat(row(enrollment).get("closed_at")).isEqualTo(yearClosedAt);
        }
    }

    @Test
    void closingAgain_doesNotRefreezeAnything() throws Exception {
        close();
        jdbc.update(
                "UPDATE academic_scores SET score_doing = 0 WHERE id_course_enrollment = ?",
                approved);

        close();

        assertThat((BigDecimal) row(approved).get("final_average")).isEqualByComparingTo("60.00");
    }

    @Test
    void reopen_clearsClosedAtOnEnrollments_keepsTheFrozenValues_andTheNextCloseOverwritesThem()
            throws Exception {
        close();

        mvc.perform(
                        put("/api/academic-years/" + academicYearId(2026) + "/reopen")
                                .header("Authorization", bearer()))
                .andExpect(status().isOk());

        for (UUID enrollment : new UUID[] {approved, roundedUp, failed, withdrawn, ungraded}) {
            assertThat(row(enrollment).get("closed_at")).isNull();
        }
        assertThat((BigDecimal) row(approved).get("final_average")).isEqualByComparingTo("60.00");
        assertThat(row(failed).get("final_result")).isEqualTo("FAILED");

        // The grade changes while the year is open; the next close freezes the new value.
        jdbc.update(
                "UPDATE academic_scores SET score_doing = 0 WHERE id_course_enrollment = ?",
                approved);
        close();

        assertThat((BigDecimal) row(approved).get("final_average")).isEqualByComparingTo("35.00");
        assertThat(row(approved).get("final_result")).isEqualTo("FAILED");
        assertThat(row(approved).get("closed_at")).isNotNull();
    }

    @Test
    void close_statementCountDoesNotScaleWithTheNumberOfEnrollments() throws Exception {
        // 2025 holds one course with one student, 2026 one course with many: same course count.
        UUID course2025 = seedCourse(teacher, "A", academicYearId(2025));
        seedEnrollment(seedStudent(), course2025);
        for (int i = 0; i < 25; i++) {
            seedEnrollment(seedStudent(), course);
        }
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);

        stats.clear();
        mvc.perform(
                        put("/api/academic-years/" + academicYearId(2025) + "/close")
                                .header("Authorization", bearer()))
                .andExpect(status().isOk());
        long small = stats.getPrepareStatementCount();

        stats.clear();
        close();
        long large = stats.getPrepareStatementCount();

        assertThat(large).as("prepared statements per closed year").isEqualTo(small);
    }
}
