package bo.edu.univalle.sis.ue6dejunio_api.application.gradebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook.GradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollmentOutcome;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.GradeInWords;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentAnnualSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentReportCard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Which final average the annual centralizer and the libreta show: the one frozen when the year
 * closed, or the one recomputed from the marks.
 *
 * <p>The recomputed one here is 70.00 and the frozen one is 61.50, so a test can tell which of the
 * two the service chose.
 */
@ExtendWith(MockitoExtension.class)
class GradebookServiceFrozenResultTest {

    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IScoreDomain scoreDomain;
    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ICourseService courseService;
    @Mock private IClassGroupDomain classGroupDomain;

    private GradebookService service;

    private UUID courseId;
    private UUID enrollment;
    private UUID student;
    private UUID classGroup;
    private PageQuery pageQuery;

    @BeforeEach
    void setUp() {
        service =
                new GradebookService(
                        enrollmentDomain,
                        scoreDomain,
                        attendanceDomain,
                        courseService,
                        classGroupDomain);
        courseId = UUID.randomUUID();
        enrollment = UUID.randomUUID();
        student = UUID.randomUUID();
        classGroup = UUID.randomUUID();
        pageQuery = PageQuery.of(0, 30);

        CourseStudent cs =
                new CourseStudent(
                        enrollment, student, "RUDE", "ID", "Nelsy", "Aiza", "Effective", "F");
        lenient()
                .when(enrollmentDomain.studentsByCourse(courseId, pageQuery))
                .thenReturn(new PageResult<>(List.of(cs), 0, 30, 1));
        lenient().when(enrollmentDomain.courseStudentById(enrollment)).thenReturn(Optional.of(cs));
        lenient().when(enrollmentDomain.courseOfEnrollment(enrollment)).thenReturn(courseId);
        lenient()
                .when(courseService.getById(courseId))
                .thenReturn(
                        new Course(
                                courseId,
                                2,
                                "2do",
                                3,
                                "C",
                                1,
                                2026,
                                UUID.randomUUID(),
                                "Nora Arnez Veliz",
                                true,
                                true));
        AcademicScore mark = score(1, "70.00");
        lenient()
                .when(scoreDomain.findByCourseEnrollmentIn(anyCollection()))
                .thenReturn(List.of(mark));
        lenient().when(scoreDomain.findByCourseEnrollment(enrollment)).thenReturn(List.of(mark));
    }

    private AcademicScore score(Integer trimester, String total) {
        return new AcademicScore(
                UUID.randomUUID(),
                enrollment,
                classGroup,
                "Lenguaje",
                trimester,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal(total),
                null,
                null);
    }

    private void outcome(boolean yearClosed, String average, AnnualResult result) {
        EnrollmentOutcome o =
                new EnrollmentOutcome(
                        enrollment,
                        yearClosed,
                        average == null ? null : new BigDecimal(average),
                        result);
        lenient()
                .when(enrollmentDomain.outcomesOf(anyCollection()))
                .thenReturn(Map.of(enrollment, o));
    }

    private StudentAnnualSummary annualRow() {
        return service.annualCentralizer(courseId, pageQuery).content().get(0);
    }

    @Test
    void annualCentralizer_closedYear_showsTheFrozenAverageAndResult() {
        outcome(true, "61.50", AnnualResult.APPROVED);

        StudentAnnualSummary row = annualRow();

        assertThat(row.finalAverage()).isEqualByComparingTo("61.50");
        assertThat(row.finalResult()).isEqualTo(AnnualResult.APPROVED);
        assertThat(row.frozen()).isTrue();
        // The trimester averages are not frozen: they are still recomputed.
        assertThat(row.trimester1Average()).isEqualByComparingTo("70.00");
    }

    @Test
    void annualCentralizer_closedYear_withNothingFrozen_keepsTheRecomputedAverageAndNoResult() {
        outcome(true, null, null);

        StudentAnnualSummary row = annualRow();

        assertThat(row.finalAverage()).isEqualByComparingTo("70.00");
        assertThat(row.finalResult()).isNull();
        assertThat(row.frozen()).isTrue();
    }

    @Test
    void annualCentralizer_openYear_ignoresWhateverAReopenedYearLeftBehind() {
        outcome(false, "61.50", AnnualResult.FAILED);

        StudentAnnualSummary row = annualRow();

        assertThat(row.finalAverage()).isEqualByComparingTo("70.00");
        assertThat(row.finalResult()).isNull();
        assertThat(row.frozen()).isFalse();
    }

    @Test
    void annualCentralizer_readsTheFrozenValuesOncePerPage() {
        outcome(true, "61.50", AnnualResult.APPROVED);

        annualRow();

        verify(enrollmentDomain, times(1)).outcomesOf(anyCollection());
    }

    @Test
    void reportCard_closedYear_showsTheFrozenAverage_itsWords_andTheResult() {
        outcome(true, "61.50", AnnualResult.APPROVED);

        StudentReportCard card = service.reportCard(enrollment);

        assertThat(card.finalAverage()).isEqualByComparingTo("61.50");
        // 61.50 prints 62: the words follow the frozen average, not the recomputed 70.
        assertThat(card.finalAverageInWords()).isEqualTo(GradeInWords.of(new BigDecimal("61.50")));
        assertThat(card.finalResult()).isEqualTo(AnnualResult.APPROVED);
        assertThat(card.frozen()).isTrue();
    }

    @Test
    void reportCard_closedYear_withNothingFrozen_keepsTheRecomputedAverageAndNoResult() {
        outcome(true, null, null);

        StudentReportCard card = service.reportCard(enrollment);

        assertThat(card.finalAverage()).isEqualByComparingTo("70.00");
        assertThat(card.finalResult()).isNull();
        assertThat(card.frozen()).isTrue();
    }

    @Test
    void reportCard_openYear_recomputesAsBefore() {
        outcome(false, "61.50", AnnualResult.FAILED);

        StudentReportCard card = service.reportCard(enrollment);

        assertThat(card.finalAverage()).isEqualByComparingTo("70.00");
        assertThat(card.finalResult()).isNull();
        assertThat(card.frozen()).isFalse();
    }
}
