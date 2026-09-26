package bo.edu.univalle.sis.ue6dejunio_api.application.gradebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook.GradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAcademicSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The Director's view of the whole school: one row per classroom, four numbers each.
 *
 * <p>What these tests pin hardest is that those numbers are the centralizer's numbers. The average
 * a course shows here has to be the mean of the very same general averages the centralizer prints
 * student by student, and a course counted as having twenty passed has to have twenty students the
 * libreta calls passed. A dashboard that computes its own version of a mark is a second opinion the
 * school never asked for, and the day the two disagree nobody knows which one to believe.
 *
 * <p>Pure unit test: no DB, no Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
class GradebookServiceCourseSummaryTest {

    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IScoreDomain scoreDomain;
    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ICourseService courseService;
    @Mock private IClassGroupDomain classGroupDomain;

    /** The row id of the academic year, not the calendar year — it is a SERIAL. */
    private static final Integer ACADEMIC_YEAR_ID = 7;

    private static final int TRIMESTER = 1;

    private GradebookService service;
    private UUID quinto;
    private UUID sexto;

    @BeforeEach
    void setUp() {
        service =
                new GradebookService(
                        enrollmentDomain,
                        scoreDomain,
                        attendanceDomain,
                        courseService,
                        classGroupDomain);
        quinto = UUID.randomUUID();
        sexto = UUID.randomUUID();
    }

    /** Without a gestión the course listing answers every year at once, mixing 2024 with 2026. */
    @Test
    void courseSummaries_refusesWithoutAGestion() {
        assertThatThrownBy(() -> service.courseSummaries(null, TRIMESTER))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void courseSummaries_countsWhoPassedAndWhoDidNot() {
        coursesOfTheYear(course(quinto, "Quinto", "B"));
        CourseStudent ana = student();
        CourseStudent beto = student();
        CourseStudent carla = student();
        rosterOf(quinto, ana, beto, carla);
        batched(
                oneAreaWorth(ana, "80.00"),
                oneAreaWorth(beto, "51.00"),
                oneAreaWorth(carla, "50.00"));

        List<CourseAcademicSummary> summaries =
                service.courseSummaries(ACADEMIC_YEAR_ID, TRIMESTER);

        assertThat(summaries)
                .singleElement()
                .satisfies(
                        s -> {
                            assertThat(s.students()).isEqualTo(3);
                            // 51 passes and 50 does not: the school's mark, taken from PassingMark
                            // and not
                            // rewritten here.
                            assertThat(s.passed()).isEqualTo(2);
                            assertThat(s.failed()).isEqualTo(1);
                        });
    }

    /** The course average is the mean of the students' general averages, rounded as they are. */
    @Test
    void courseSummaries_averagesTheStudentsOwnAverages() {
        coursesOfTheYear(course(quinto, "Quinto", "B"));
        CourseStudent ana = student();
        CourseStudent beto = student();
        rosterOf(quinto, ana, beto);
        batched(oneAreaWorth(ana, "90.00"), oneAreaWorth(beto, "70.00"));

        List<CourseAcademicSummary> summaries =
                service.courseSummaries(ACADEMIC_YEAR_ID, TRIMESTER);

        assertThat(summaries.get(0).average()).isEqualByComparingTo("80.00");
    }

    /** And it names the classroom the way the school says it out loud. */
    @Test
    void courseSummaries_carriesTheGradeAndParallel() {
        coursesOfTheYear(course(quinto, "Quinto", "B"));
        rosterOf(quinto, student());
        batched();

        assertThat(summariesOfYear())
                .singleElement()
                .satisfies(
                        s -> {
                            assertThat(s.gradeName()).isEqualTo("Quinto");
                            assertThat(s.parallelName()).isEqualTo("B");
                        });
    }

    /**
     * A classroom nobody has graded yet has no average.
     *
     * <p>Zero would read as a course that failed, which is the opposite of what happened: the marks
     * have not been entered. The count of students is still real, and that is what says the course
     * exists and is simply waiting.
     */
    @Test
    void courseSummaries_aCourseWithoutMarksHasNoAverage() {
        coursesOfTheYear(course(quinto, "Quinto", "B"));
        rosterOf(quinto, student(), student());
        batched();

        assertThat(summariesOfYear())
                .singleElement()
                .satisfies(
                        s -> {
                            assertThat(s.average()).isNull();
                            assertThat(s.students()).isEqualTo(2);
                            // Nobody passed, and nobody failed either. Counting the ungraded as
                            // failed would
                            // publish a reprobación the school never declared.
                            assertThat(s.passed()).isZero();
                            assertThat(s.failed()).isZero();
                        });
    }

    /**
     * A course with no enrolments still appears: an empty classroom is a fact, not an absence.
     *
     * <p>And the scores are not asked for at all. An {@code IN ()} over an empty set is invalid SQL
     * on some dialects and a pointless round trip on every one of them, so the guard is asserted
     * here rather than left to be discovered by a school that opens the year before enrolling
     * anybody.
     */
    @Test
    void courseSummaries_anEmptyCourseStillHasARowAndCostsNoScoreQuery() {
        coursesOfTheYear(course(quinto, "Quinto", "B"));
        rosterOf(quinto);

        assertThat(summariesOfYear())
                .singleElement()
                .satisfies(
                        s -> {
                            assertThat(s.students()).isZero();
                            assertThat(s.average()).isNull();
                        });
        verify(scoreDomain, never()).findByCourseEnrollmentIn(anyCollection());
    }

    /** Only the trimester asked for. A row of another trimester is another number entirely. */
    @Test
    void courseSummaries_readsOnlyTheTrimesterAsked() {
        coursesOfTheYear(course(quinto, "Quinto", "B"));
        CourseStudent ana = student();
        rosterOf(quinto, ana);
        batched(score(ana, "Lenguaje", 1, "90.00"), score(ana, "Lenguaje", 2, "40.00"));

        assertThat(summariesOfYear().get(0).average()).isEqualByComparingTo("90.00");
    }

    /**
     * The whole school in a fixed number of queries.
     *
     * <p>One listing of courses, one roster read per course and ONE batch of scores — not one score
     * query per classroom, and not one per student. This is the same N+1 that was taken out of the
     * risk list: a Director's dashboard that costs thirty round trips is a Director's dashboard
     * nobody opens twice.
     */
    @Test
    void courseSummaries_readsEveryScoreInOneBatch() {
        coursesOfTheYear(course(quinto, "Quinto", "B"), course(sexto, "Sexto", "A"));
        rosterOf(quinto, student());
        rosterOf(sexto, student());
        batched();

        summariesOfYear();

        verify(scoreDomain, times(1)).findByCourseEnrollmentIn(anyCollection());
    }

    /** The order is the one the course listing gave, so two readings show the same table. */
    @Test
    void courseSummaries_keepsTheOrderOfTheCourseListing() {
        coursesOfTheYear(course(sexto, "Sexto", "A"), course(quinto, "Quinto", "B"));
        rosterOf(sexto, student());
        rosterOf(quinto, student());
        batched();

        assertThat(summariesOfYear())
                .extracting(CourseAcademicSummary::courseId)
                .containsExactly(sexto, quinto);
    }

    // ---------------------------------------------------------------- fixtures

    private List<CourseAcademicSummary> summariesOfYear() {
        return service.courseSummaries(ACADEMIC_YEAR_ID, TRIMESTER);
    }

    private void coursesOfTheYear(Course... courses) {
        when(courseService.allOfYear(ACADEMIC_YEAR_ID)).thenReturn(List.of(courses));
    }

    private void rosterOf(UUID courseId, CourseStudent... students) {
        when(enrollmentDomain.studentsByCourse(eq(courseId), any(PageQuery.class)))
                .thenReturn(new PageResult<>(List.of(students), 0, 200, students.length));
    }

    private void batched(AcademicScore... scores) {
        when(scoreDomain.findByCourseEnrollmentIn(anyCollection())).thenReturn(List.of(scores));
    }

    private static CourseStudent student() {
        return new CourseStudent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "RUDE",
                "ID",
                "Ana",
                "Perez",
                "Effective",
                "F");
    }

    private static Course course(UUID id, String gradeName, String parallelName) {
        return new Course(
                id,
                5,
                gradeName,
                2,
                parallelName,
                ACADEMIC_YEAR_ID,
                2026,
                UUID.randomUUID(),
                "Ana Perez",
                true);
    }

    private static AcademicScore oneAreaWorth(CourseStudent cs, String total) {
        return score(cs, "Lenguaje", TRIMESTER, total);
    }

    private static AcademicScore score(
            CourseStudent cs, String subject, Integer trimester, String total) {
        return new AcademicScore(
                UUID.randomUUID(),
                cs.courseEnrollmentId(),
                UUID.randomUUID(),
                subject,
                trimester,
                null,
                null,
                null,
                null,
                new BigDecimal(total),
                UUID.randomUUID(),
                LocalDateTime.of(2026, 4, 10, 8, 0));
    }
}
