package bo.edu.univalle.sis.ue6dejunio_api.application.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook.GradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualSubjectScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentAnnualSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentTrimesterSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The annual centralizer, which is three of the school's sheets at once: the per-area matrix with
 * its {@code PR} column, the three trimester averages, and the final average the year-end ranking
 * is ordered by. The arithmetic here is taken from the school's own spreadsheet, not invented — the
 * numbers in these tests are the ones their template produces.
 *
 * <p>Pure unit test: no DB, no Testcontainers. What it pins is the averaging and the single batched
 * load, which is where a per-student query would silently come back.
 */
@ExtendWith(MockitoExtension.class)
class GradebookServiceAnnualTest {

    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IScoreDomain scoreDomain;
    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ICourseService courseService;
    @Mock private IClassGroupDomain classGroupDomain;

    private GradebookService service;

    private UUID courseId;
    private UUID enrollmentA;
    private UUID enrollmentB;
    private UUID studentA;
    private UUID studentB;
    private UUID classGroupLang;
    private UUID classGroupMath;
    private UUID classGroupComputing;
    private PageQuery pageQuery;

    @BeforeEach
    void setUp() {
        service = new GradebookService(enrollmentDomain, scoreDomain, attendanceDomain,
            courseService, classGroupDomain);
        courseId = UUID.randomUUID();
        enrollmentA = UUID.randomUUID();
        enrollmentB = UUID.randomUUID();
        studentA = UUID.randomUUID();
        studentB = UUID.randomUUID();
        classGroupLang = UUID.randomUUID();
        classGroupMath = UUID.randomUUID();
        classGroupComputing = UUID.randomUUID();
        pageQuery = PageQuery.of(0, 30);
    }

    @Test
    void annualCentralizer_areaAverageIsTheMeanOfItsThreeTrimesters() {
        rosterOf(courseStudent(enrollmentA, studentA, "Nelsy", "Aiza"));
        // The worked example from the school's own template: 10, 10 and 100 give the 40 their
        // PR column shows. Nothing is weighted — a trimester worth 100 does not outrank the others.
        batched(
            score(enrollmentA, classGroupLang, "Lenguaje", 1, "10.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 2, "10.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 3, "100.00"),
            score(enrollmentA, classGroupMath, "Matematica", 1, "1.00"),
            score(enrollmentA, classGroupMath, "Matematica", 2, "10.00"),
            score(enrollmentA, classGroupMath, "Matematica", 3, "10.00"));

        StudentAnnualSummary row = service.annualCentralizer(courseId, pageQuery).content().get(0);

        AnnualSubjectScore lang = subject(row, "Lenguaje");
        assertThat(lang.trimester1()).isEqualByComparingTo("10.00");
        assertThat(lang.trimester2()).isEqualByComparingTo("10.00");
        assertThat(lang.trimester3()).isEqualByComparingTo("100.00");
        assertThat(lang.average()).isEqualByComparingTo("40.00");

        assertThat(subject(row, "Matematica").average()).isEqualByComparingTo("7.00");
    }

    @Test
    void annualCentralizer_averageThatDoesNotDivideEvenly_isKeptAtTwoDecimalsHalfUp() {
        rosterOf(courseStudent(enrollmentA, studentA, "Nelsy", "Aiza"));
        // 31 / 3 = 10.333…, and 0.505 sits exactly on the rounding boundary. Marks are read off
        // paper by a teacher, so a half that rounded down would show a mark nobody wrote and
        // would not add back up to the average printed beside it.
        batched(
            score(enrollmentA, classGroupLang, "Lenguaje", 1, "10.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 2, "10.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 3, "11.00"),
            score(enrollmentA, classGroupMath, "Matematica", 1, "1.00"),
            score(enrollmentA, classGroupMath, "Matematica", 2, "0.01"));

        StudentAnnualSummary row = service.annualCentralizer(courseId, pageQuery).content().get(0);

        assertThat(subject(row, "Lenguaje").average()).isEqualByComparingTo("10.33");
        assertThat(subject(row, "Matematica").average()).isEqualByComparingTo("0.51");
        // Two decimals, not a longer tail that a screen would truncate differently.
        assertThat(subject(row, "Lenguaje").average().scale()).isEqualTo(2);
    }

    @Test
    void annualCentralizer_finalAverageIsTheMeanOfTheAreaAverages() {
        rosterOf(courseStudent(enrollmentA, studentA, "Nelsy", "Aiza"));
        batched(
            score(enrollmentA, classGroupLang, "Lenguaje", 1, "10.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 2, "10.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 3, "100.00"),
            score(enrollmentA, classGroupMath, "Matematica", 1, "1.00"),
            score(enrollmentA, classGroupMath, "Matematica", 2, "10.00"),
            score(enrollmentA, classGroupMath, "Matematica", 3, "10.00"));

        StudentAnnualSummary row = service.annualCentralizer(courseId, pageQuery).content().get(0);

        // (40 + 7) / 2.
        assertThat(row.finalAverage()).isEqualByComparingTo("23.50");
    }

    @Test
    void annualCentralizer_trimesterAveragesMatchWhatTheTrimesterSheetReturns() {
        rosterOf(courseStudent(enrollmentA, studentA, "Nelsy", "Aiza"));
        List<AcademicScore> scores = List.of(
            score(enrollmentA, classGroupLang, "Lenguaje", 1, "10.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 2, "10.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 3, "100.00"),
            score(enrollmentA, classGroupMath, "Matematica", 1, "1.00"),
            score(enrollmentA, classGroupMath, "Matematica", 2, "10.00"),
            score(enrollmentA, classGroupMath, "Matematica", 3, "10.00"));
        when(scoreDomain.findByCourseEnrollmentIn(anyCollection())).thenReturn(scores);

        StudentAnnualSummary annual = service.annualCentralizer(courseId, pageQuery).content().get(0);

        assertThat(annual.trimester1Average()).isEqualByComparingTo("5.50");
        assertThat(annual.trimester2Average()).isEqualByComparingTo("10.00");
        assertThat(annual.trimester3Average()).isEqualByComparingTo("55.00");

        // The two screens must never disagree about one student: whatever the trimester
        // centralizer prints as the general average is what lands in this row's column.
        for (int trimester = 1; trimester <= 3; trimester++) {
            StudentTrimesterSummary perTrimester =
                service.centralizer(courseId, trimester, pageQuery).content().get(0);
            assertThat(annualColumn(annual, trimester))
                .isEqualByComparingTo(perTrimester.generalAverage());
        }
    }

    @Test
    void annualCentralizer_areaMissingFromATrimester_averagesOnlyTheTrimestersItWasGradedIn() {
        rosterOf(courseStudent(enrollmentA, studentA, "Nelsy", "Aiza"));
        // A subject that starts mid-year. Dividing by three regardless would invent two failed
        // trimesters for it and drag the student's year down for a course they never had.
        batched(
            score(enrollmentA, classGroupComputing, "Computacion", 3, "90.00"));

        StudentAnnualSummary row = service.annualCentralizer(courseId, pageQuery).content().get(0);

        AnnualSubjectScore computing = subject(row, "Computacion");
        assertThat(computing.trimester1()).isNull();
        assertThat(computing.trimester2()).isNull();
        assertThat(computing.trimester3()).isEqualByComparingTo("90.00");
        assertThat(computing.average()).isEqualByComparingTo("90.00");
    }

    @Test
    void annualCentralizer_incompleteMatrix_finalAverageFollowsTheAreasNotTheTrimesters() {
        rosterOf(courseStudent(enrollmentA, studentA, "Nelsy", "Aiza"));
        batched(
            score(enrollmentA, classGroupLang, "Lenguaje", 1, "60.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 2, "60.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 3, "60.00"),
            score(enrollmentA, classGroupComputing, "Computacion", 3, "90.00"));

        StudentAnnualSummary row = service.annualCentralizer(courseId, pageQuery).content().get(0);

        // The school's template carries two final averages that agree only while every area is
        // graded in all three trimesters. Here they part: the areas give (60 + 90) / 2 = 75, the
        // trimester averages give (60 + 60 + 75) / 3 = 65. The area column is the one the
        // year-end sheet titles PROMEDIO FINAL, so it is the one this field reports.
        assertThat(row.finalAverage()).isEqualByComparingTo("75.00");
        assertThat(row.trimester3Average()).isEqualByComparingTo("75.00");
    }

    @Test
    void annualCentralizer_ungradedTotalCountsAsZero_sameAsTheTrimesterSheet() {
        rosterOf(courseStudent(enrollmentA, studentA, "Nelsy", "Aiza"));
        // A row exists but carries no total: the teacher opened the subject and has not closed it.
        // The trimester sheet already counts that as a zero in the denominator, and the annual
        // view has to read the same, or one screen calls a student approved and the other does not.
        batched(
            score(enrollmentA, classGroupLang, "Lenguaje", 1, "60.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 2, null));

        AnnualSubjectScore lang =
            subject(service.annualCentralizer(courseId, pageQuery).content().get(0), "Lenguaje");

        assertThat(lang.trimester2()).isNull();
        assertThat(lang.average()).isEqualByComparingTo("30.00");
    }

    @Test
    void annualCentralizer_noScores_keepsTheRowWithNullAverages() {
        rosterOf(courseStudent(enrollmentA, studentA, "Nelsy", "Aiza"));
        batched();

        StudentAnnualSummary row = service.annualCentralizer(courseId, pageQuery).content().get(0);

        assertThat(row.subjects()).isEmpty();
        assertThat(row.trimester1Average()).isNull();
        assertThat(row.trimester2Average()).isNull();
        assertThat(row.trimester3Average()).isNull();
        assertThat(row.finalAverage()).isNull();
    }

    @Test
    void annualCentralizer_subjectsAreOrderedByName_soTheSheetColumnsNeverShuffle() {
        rosterOf(courseStudent(enrollmentA, studentA, "Nelsy", "Aiza"));
        // Deliberately not alphabetical, and not grouped: the order a batched query answers in is
        // not guaranteed, and a report whose columns move between two loads is unreadable.
        batched(
            score(enrollmentA, classGroupMath, "Matematica", 1, "70.00"),
            score(enrollmentA, classGroupComputing, "Computacion", 1, "80.00"),
            score(enrollmentA, classGroupLang, "Lenguaje", 2, "90.00"),
            score(enrollmentA, classGroupMath, "Matematica", 2, "60.00"));

        StudentAnnualSummary row = service.annualCentralizer(courseId, pageQuery).content().get(0);

        assertThat(row.subjects()).extracting(AnnualSubjectScore::subjectName)
            .containsExactly("Computacion", "Lenguaje", "Matematica");
    }

    @Test
    void annualCentralizer_batchesTheWholePage_withNoCrossStudentBleed() {
        rosterOf(
            courseStudent(enrollmentA, studentA, "Nelsy", "Aiza"),
            courseStudent(enrollmentB, studentB, "Joana", "Alvarez"));
        batched(
            score(enrollmentA, classGroupLang, "Lenguaje", 1, "60.00"),
            score(enrollmentB, classGroupMath, "Matematica", 1, "80.00"));

        PageResult<StudentAnnualSummary> result = service.annualCentralizer(courseId, pageQuery);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).subjects()).extracting(AnnualSubjectScore::subjectName)
            .containsExactly("Lenguaje");
        assertThat(result.content().get(1).subjects()).extracting(AnnualSubjectScore::subjectName)
            .containsExactly("Matematica");

        verify(scoreDomain, times(1)).findByCourseEnrollmentIn(anyCollection());
        verify(scoreDomain, never()).findByCourseEnrollment(any());
    }

    @Test
    void annualCentralizer_emptyPage_skipsTheBatchQuery() {
        when(enrollmentDomain.studentsByCourse(courseId, pageQuery)).thenReturn(page(List.of()));

        assertThat(service.annualCentralizer(courseId, pageQuery).content()).isEmpty();
        verify(scoreDomain, never()).findByCourseEnrollmentIn(anyCollection());
    }

    @Test
    void annualCentralizer_keepsTheUnfilteredRoster_soAWithdrawnStudentKeepsTheirYear() {
        rosterOf(courseStudent(enrollmentA, studentA, "Nelsy", "Aiza"));
        batched();

        service.annualCentralizer(courseId, pageQuery);

        // Same rule the trimester centralizer follows: a student who left still has a record of
        // the year they were here, and the year-end sheet is exactly where that record is read.
        verify(enrollmentDomain, never()).activeStudentsByCourse(any(), any());
    }

    private void rosterOf(CourseStudent... students) {
        when(enrollmentDomain.studentsByCourse(courseId, pageQuery)).thenReturn(page(List.of(students)));
    }

    private void batched(AcademicScore... scores) {
        if (scores.length == 0) {
            when(scoreDomain.findByCourseEnrollmentIn(anyCollection())).thenReturn(List.of());
            return;
        }
        when(scoreDomain.findByCourseEnrollmentIn(anyCollection())).thenReturn(List.of(scores));
    }

    private static AnnualSubjectScore subject(StudentAnnualSummary row, String subjectName) {
        return row.subjects().stream()
            .filter(s -> subjectName.equals(s.subjectName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no subject named " + subjectName));
    }

    private static BigDecimal annualColumn(StudentAnnualSummary row, int trimester) {
        return switch (trimester) {
            case 1 -> row.trimester1Average();
            case 2 -> row.trimester2Average();
            default -> row.trimester3Average();
        };
    }

    private static CourseStudent courseStudent(UUID enrollmentId, UUID studentId,
                                               String names, String lastNames) {
        return new CourseStudent(enrollmentId, studentId, "RUDE", "ID", names, lastNames, "Effective", "F");
    }

    private static AcademicScore score(UUID enrollmentId, UUID classGroupId, String subject,
                                       Integer trimester, String total) {
        return new AcademicScore(UUID.randomUUID(), enrollmentId, classGroupId, subject, trimester,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            total == null ? null : new BigDecimal(total), null, null);
    }

    /** One full page of these rows, the shape a domain port returns. */
    private static <T> PageResult<T> page(List<T> rows) {
        return new PageResult<>(rows, 0, 30, rows.size());
    }
}
