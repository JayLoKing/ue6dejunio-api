package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.FrozenResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The three year-wide enrollment writes behind closing and reopening a year. */
class CourseEnrollmentFreezeIT extends AbstractIntegrationTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 2, 12, 0);

    @Autowired private ICourseEnrollmentDomain enrollments;

    private Integer year2026;
    private UUID inYear;
    private UUID withdrawnInYear;
    private UUID otherYear;

    @BeforeEach
    void seed() {
        UUID teacher = seedUser("Teacher", false);
        year2026 = academicYearId(2026);
        UUID course = seedCourse(teacher, "A", year2026);
        UUID course2025 = seedCourse(teacher, "A", academicYearId(2025));
        inYear = seedEnrollment(seedStudent(), course);
        withdrawnInYear = seedEnrollment(seedStudent(), course);
        jdbc.update(
                "UPDATE course_enrollments SET status = 'Withdrawn' WHERE id_course_enrollment = ?",
                withdrawnInYear);
        otherYear = seedEnrollment(seedStudent(), course2025);
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap(
                "SELECT final_average, final_result, closed_at FROM course_enrollments "
                        + "WHERE id_course_enrollment = ?",
                id);
    }

    @Test
    void closeEnrollmentsOfYear_stampsEveryEnrollmentOfThatYearOnly() {
        enrollments.closeEnrollmentsOfYear(year2026, AT);

        assertThat(row(inYear).get("closed_at")).isEqualTo(Timestamp.valueOf(AT));
        assertThat(row(withdrawnInYear).get("closed_at")).isEqualTo(Timestamp.valueOf(AT));
        assertThat(row(otherYear).get("closed_at")).isNull();
    }

    @Test
    void closeEnrollmentsOfYear_clearsAStaleResultOfAWithdrawnEnrollment() {
        jdbc.update(
                "UPDATE course_enrollments SET final_average = 70, final_result = 'APPROVED' "
                        + "WHERE id_course_enrollment = ?",
                withdrawnInYear);

        enrollments.closeEnrollmentsOfYear(year2026, AT);

        assertThat(row(withdrawnInYear).get("final_average")).isNull();
        assertThat(row(withdrawnInYear).get("final_result")).isNull();
    }

    @Test
    void freezeResults_writesAverageAndResult_andNullsForNoData() {
        enrollments.freezeResults(
                List.of(new FrozenResult(inYear, new BigDecimal("50.60"), AnnualResult.APPROVED)));

        assertThat((BigDecimal) row(inYear).get("final_average"))
                .isEqualByComparingTo(new BigDecimal("50.60"));
        assertThat(row(inYear).get("final_result")).isEqualTo("APPROVED");

        enrollments.freezeResults(List.of(new FrozenResult(inYear, null, null)));

        assertThat(row(inYear).get("final_average")).isNull();
        assertThat(row(inYear).get("final_result")).isNull();
    }

    @Test
    void freezeResults_neverTouchesAWithdrawnEnrollment() {
        enrollments.freezeResults(
                List.of(
                        new FrozenResult(
                                withdrawnInYear, new BigDecimal("80.00"), AnnualResult.APPROVED)));

        assertThat(row(withdrawnInYear).get("final_average")).isNull();
        assertThat(row(withdrawnInYear).get("final_result")).isNull();
    }

    @Test
    void reopenEnrollmentsOfYear_clearsClosedAt_andKeepsTheFrozenValues() {
        enrollments.closeEnrollmentsOfYear(year2026, AT);
        enrollments.freezeResults(
                List.of(new FrozenResult(inYear, new BigDecimal("42.00"), AnnualResult.FAILED)));

        enrollments.reopenEnrollmentsOfYear(year2026);

        assertThat(row(inYear).get("closed_at")).isNull();
        assertThat(row(withdrawnInYear).get("closed_at")).isNull();
        assertThat(row(inYear).get("final_result")).isEqualTo("FAILED");
        assertThat((BigDecimal) row(inYear).get("final_average"))
                .isEqualByComparingTo(new BigDecimal("42.00"));
    }
}
