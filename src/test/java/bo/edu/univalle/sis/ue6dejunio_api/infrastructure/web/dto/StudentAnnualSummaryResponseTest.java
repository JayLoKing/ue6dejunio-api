package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualSubjectScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentAnnualSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mapping the three year-end sheets actually read. The service's arithmetic is proved
 * elsewhere; what is proved here is that none of it is dropped, reordered, or turned into a zero
 * on the way out — the one place where a correct average can still reach the school wrong.
 */
class StudentAnnualSummaryResponseTest {

    private static final UUID ENROLLMENT = UUID.randomUUID();
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID CLASS_GROUP_LANG = UUID.randomUUID();
    private static final UUID CLASS_GROUP_COMPUTING = UUID.randomUUID();

    @Test
    void from_carriesEveryFieldOfTheStudentAndTheirAreas() {
        StudentAnnualSummary summary = new StudentAnnualSummary(ENROLLMENT, STUDENT,
            "AIZA ARICOMA NELSY",
            List.of(new AnnualSubjectScore(CLASS_GROUP_LANG, "Lenguaje",
                new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("100.00"),
                new BigDecimal("40.00"))),
            new BigDecimal("5.50"), new BigDecimal("10.00"), new BigDecimal("55.00"),
            new BigDecimal("23.50"));

        StudentAnnualSummaryResponse response = StudentAnnualSummaryResponse.from(summary);

        assertThat(response.courseEnrollmentId()).isEqualTo(ENROLLMENT);
        assertThat(response.studentId()).isEqualTo(STUDENT);
        assertThat(response.fullName()).isEqualTo("AIZA ARICOMA NELSY");
        assertThat(response.finalAverage()).isEqualByComparingTo("23.50");

        assertThat(response.subjects()).hasSize(1);
        StudentAnnualSummaryResponse.Subject subject = response.subjects().get(0);
        assertThat(subject.classGroupId()).isEqualTo(CLASS_GROUP_LANG);
        assertThat(subject.subjectName()).isEqualTo("Lenguaje");
        assertThat(subject.trimester1()).isEqualByComparingTo("10.00");
        assertThat(subject.trimester2()).isEqualByComparingTo("10.00");
        assertThat(subject.trimester3()).isEqualByComparingTo("100.00");
        assertThat(subject.average()).isEqualByComparingTo("40.00");
    }

    @Test
    void from_keepsTheTrimesterAveragesInTrimesterOrder() {
        // Three distinct values, so a transposition cannot pass unnoticed: the sheet reads these
        // by position, and swapping two would move a student's whole trimester.
        StudentAnnualSummary summary = summaryWith(
            new BigDecimal("11.00"), new BigDecimal("22.00"), new BigDecimal("33.00"));

        List<BigDecimal> averages = StudentAnnualSummaryResponse.from(summary).trimesterAverages();

        assertThat(averages).hasSize(3);
        assertThat(averages.get(0)).isEqualByComparingTo("11.00");
        assertThat(averages.get(1)).isEqualByComparingTo("22.00");
        assertThat(averages.get(2)).isEqualByComparingTo("33.00");
    }

    @Test
    void from_ungradedTrimesterStaysNull_andStillHoldsItsPlace() {
        // The list is always three long. Dropping the nulls would shift the third trimester into
        // the first column, and the sheet would print a mark under the wrong heading.
        StudentAnnualSummary summary = summaryWith(null, null, new BigDecimal("90.00"));

        List<BigDecimal> averages = StudentAnnualSummaryResponse.from(summary).trimesterAverages();

        assertThat(averages).hasSize(3);
        assertThat(averages.get(0)).isNull();
        assertThat(averages.get(1)).isNull();
        assertThat(averages.get(2)).isEqualByComparingTo("90.00");
    }

    @Test
    void from_neverGradedArea_keepsNullsInsteadOfZeros() {
        StudentAnnualSummary summary = new StudentAnnualSummary(ENROLLMENT, STUDENT, "Nelsy",
            List.of(new AnnualSubjectScore(CLASS_GROUP_COMPUTING, "Computacion",
                null, null, new BigDecimal("90.00"), new BigDecimal("90.00"))),
            null, null, new BigDecimal("90.00"), new BigDecimal("90.00"));

        StudentAnnualSummaryResponse.Subject subject =
            StudentAnnualSummaryResponse.from(summary).subjects().get(0);

        assertThat(subject.trimester1()).isNull();
        assertThat(subject.trimester2()).isNull();
        assertThat(subject.average()).isEqualByComparingTo("90.00");
    }

    @Test
    void from_nothingGradedYet_mapsToNullsAndAnEmptyAreaList() {
        StudentAnnualSummary summary = new StudentAnnualSummary(ENROLLMENT, STUDENT, "Nelsy",
            List.of(), null, null, null, null);

        StudentAnnualSummaryResponse response = StudentAnnualSummaryResponse.from(summary);

        assertThat(response.subjects()).isEmpty();
        assertThat(response.trimesterAverages()).containsExactly(null, null, null);
        assertThat(response.finalAverage()).isNull();
    }

    @Test
    void from_keepsTheAreaOrderTheServiceChose() {
        StudentAnnualSummary summary = new StudentAnnualSummary(ENROLLMENT, STUDENT, "Nelsy",
            List.of(
                new AnnualSubjectScore(CLASS_GROUP_COMPUTING, "Computacion",
                    null, null, new BigDecimal("90.00"), new BigDecimal("90.00")),
                new AnnualSubjectScore(CLASS_GROUP_LANG, "Lenguaje",
                    new BigDecimal("60.00"), new BigDecimal("60.00"), new BigDecimal("60.00"),
                    new BigDecimal("60.00"))),
            new BigDecimal("60.00"), new BigDecimal("60.00"), new BigDecimal("75.00"),
            new BigDecimal("75.00"));

        assertThat(StudentAnnualSummaryResponse.from(summary).subjects())
            .extracting(StudentAnnualSummaryResponse.Subject::subjectName)
            .containsExactly("Computacion", "Lenguaje");
    }

    private static StudentAnnualSummary summaryWith(BigDecimal first, BigDecimal second,
                                                    BigDecimal third) {
        return new StudentAnnualSummary(ENROLLMENT, STUDENT, "Nelsy", List.of(),
            first, second, third, new BigDecimal("22.00"));
    }
}
