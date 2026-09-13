package bo.edu.univalle.sis.ue6dejunio_api.application.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook.GradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.HonorRollEntry;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The cuadro de honor: the podium of a course, and the podium of the whole school.
 *
 * <p>What these tests pin above all is that the ranking is ordered by the very same final average
 * the libreta prints. That average is a mean of means rounded twice, and a report that recomputed
 * it its own way would sooner or later put a student on the podium the libreta says is second.
 *
 * <p>Pure unit test: no DB, no Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
class GradebookServiceHonorRollTest {

    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IScoreDomain scoreDomain;
    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ICourseService courseService;
    @Mock private IClassGroupDomain classGroupDomain;

    /**
     * The row id of the academic year, not the calendar year — {@code id_academic_year} is a
     * SERIAL, and a test that used 2026 here would teach the next reader the wrong thing.
     */
    private static final Integer ACADEMIC_YEAR_ID = 7;

    private GradebookService service;

    private UUID courseId;

    @BeforeEach
    void setUp() {
        service = new GradebookService(enrollmentDomain, scoreDomain, attendanceDomain,
            courseService, classGroupDomain);
        courseId = UUID.randomUUID();
    }

    @Test
    void honorRoll_putsTheBestFirstAndCutsAtThePlacesAsked() {
        courseIs(course(courseId, "Quinto", "B"));
        CourseStudent first = student("Ana", "Perez");
        CourseStudent second = student("Beto", "Quispe");
        CourseStudent third = student("Carla", "Rojas");
        CourseStudent fourth = student("Dario", "Soto");
        rosterOf(first, second, third, fourth);
        batched(
            oneAreaWorth(first, "95.00"),
            oneAreaWorth(second, "80.00"),
            oneAreaWorth(third, "88.00"),
            oneAreaWorth(fourth, "70.00"));

        List<HonorRollEntry> podium = service.honorRoll(courseId, 3);

        assertThat(podium).hasSize(3);
        assertThat(podium).extracting(HonorRollEntry::fullName)
            .containsExactly(first.fullName(), third.fullName(), second.fullName());
        assertThat(podium).extracting(HonorRollEntry::position).containsExactly(1, 2, 3);
        assertThat(podium.get(0).finalAverage()).isEqualByComparingTo("95.00");
    }

    /**
     * A student nobody graded is not the worst of the course, and putting them last would say they
     * were judged. They are simply not on a podium.
     */
    @Test
    void honorRoll_leavesOutTheStudentWithNothingGraded() {
        courseIs(course(courseId, "Quinto", "B"));
        CourseStudent graded = student("Ana", "Perez");
        CourseStudent ungraded = student("Beto", "Quispe");
        rosterOf(graded, ungraded);
        batched(oneAreaWorth(graded, "60.00"));

        List<HonorRollEntry> podium = service.honorRoll(courseId, 3);

        assertThat(podium).extracting(HonorRollEntry::fullName)
            .containsExactly(graded.fullName());
    }

    /**
     * Two students on the same average still have to come out in the same order on two readings,
     * or the school prints two different podiums from one year. The name the podium itself shows
     * breaks the tie, so what decides the order is something the reader can see.
     */
    @Test
    void honorRoll_breaksATieByNameSoThePodiumHoldsStill() {
        courseIs(course(courseId, "Quinto", "B"));
        CourseStudent ana = student("Ana", "Perez");
        CourseStudent beto = student("Beto", "Quispe");
        rosterOf(beto, ana);
        batched(oneAreaWorth(beto, "90.00"), oneAreaWorth(ana, "90.00"));

        List<HonorRollEntry> podium = service.honorRoll(courseId, 2);

        assertThat(podium).extracting(HonorRollEntry::fullName)
            .containsExactly(ana.fullName(), beto.fullName());
    }

    /**
     * The roster arrives one page at a time. A podium built from the first page only is a podium
     * that silently drops the best student of a large course.
     */
    @Test
    void honorRoll_readsTheWholeRosterAndNotJustTheFirstPage() {
        courseIs(course(courseId, "Quinto", "B"));
        CourseStudent onFirstPage = student("Ana", "Perez");
        CourseStudent onSecondPage = student("Beto", "Quispe");
        // Two pages: the totals say there is more to read than the first page carried.
        when(enrollmentDomain.studentsByCourse(eq(courseId), any(PageQuery.class)))
            .thenReturn(new PageResult<>(List.of(onFirstPage), 0, 200, 2L))
            .thenReturn(new PageResult<>(List.of(onSecondPage), 1, 200, 2L));
        batched(oneAreaWorth(onFirstPage, "70.00"), oneAreaWorth(onSecondPage, "99.00"));

        List<HonorRollEntry> podium = service.honorRoll(courseId, 3);

        assertThat(podium).extracting(HonorRollEntry::fullName)
            .containsExactly(onSecondPage.fullName(), onFirstPage.fullName());
    }

    /**
     * The reason the paged read is sound at all.
     *
     * <p>{@code LIMIT/OFFSET} with no {@code ORDER BY} lets Postgres hand back a row on two pages
     * and never hand back another. The loop would then stop on the inflated count with a student
     * unread — and the student it dropped could be the one the podium exists to name. The sort has
     * to be total, so two students sharing a name cannot tie either.
     */
    @Test
    void honorRoll_asksForTheRosterInAnOrderThatCannotShiftBetweenPages() {
        courseIs(course(courseId, "Quinto", "B"));
        rosterOf(student("Ana", "Perez"));
        batched();

        service.honorRoll(courseId, 3);

        ArgumentCaptor<PageQuery> asked = ArgumentCaptor.forClass(PageQuery.class);
        verify(enrollmentDomain).studentsByCourse(eq(courseId), asked.capture());
        assertThat(asked.getValue().sort()).extracting(SortField::property)
            .containsExactly("student.lastNames", "student.names", "id");
    }

    /**
     * Without a gestión the course query answers every year at once, and the podium would mix a
     * student of 2024 with one of 2026 — two years the school never ranked against each other.
     */
    @Test
    void institutionHonorRoll_refusesToBuildAPodiumWithoutAGestion() {
        assertThatThrownBy(() -> service.institutionHonorRoll(null, 10))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void honorRoll_isEmptyWhenTheCourseHasNobodyGraded() {
        courseIs(course(courseId, "Quinto", "B"));
        rosterOf(student("Ana", "Perez"));
        batched();

        assertThat(service.honorRoll(courseId, 3)).isEmpty();
    }

    /**
     * The school's podium is not the concatenation of the courses': it is the best of the whole
     * building. Taking the same number of places from each course before merging is exact, not an
     * approximation — a student who is eleventh in their own classroom already has ten ahead of
     * them, so they cannot be in the school's top ten.
     */
    @Test
    void institutionHonorRoll_takesTheBestOfEveryCourseAndCutsOnceMore() {
        UUID otherCourseId = UUID.randomUUID();
        Course quinto = course(courseId, "Quinto", "B");
        Course sexto = course(otherCourseId, "Sexto", "A");
        coursesOfTheYear(quinto, sexto);

        CourseStudent bestOfQuinto = student("Ana", "Perez");
        CourseStudent restOfQuinto = student("Beto", "Quispe");
        CourseStudent bestOfSexto = student("Carla", "Rojas");
        CourseStudent restOfSexto = student("Dario", "Soto");
        rosterOfCourse(courseId, bestOfQuinto, restOfQuinto);
        rosterOfCourse(otherCourseId, bestOfSexto, restOfSexto);
        batched(
            oneAreaWorth(bestOfQuinto, "91.00"),
            oneAreaWorth(restOfQuinto, "60.00"),
            oneAreaWorth(bestOfSexto, "97.00"),
            oneAreaWorth(restOfSexto, "55.00"));

        List<HonorRollEntry> podium = service.institutionHonorRoll(ACADEMIC_YEAR_ID,2);

        assertThat(podium).extracting(HonorRollEntry::fullName)
            .containsExactly(bestOfSexto.fullName(), bestOfQuinto.fullName());
        assertThat(podium).extracting(HonorRollEntry::position).containsExactly(1, 2);
    }

    /** A school-wide podium that does not say which classroom a student came from is unreadable. */
    @Test
    void institutionHonorRoll_namesTheCourseEachStudentCameFrom() {
        Course quinto = course(courseId, "Quinto", "B");
        coursesOfTheYear(quinto);
        CourseStudent ana = student("Ana", "Perez");
        rosterOfCourse(courseId, ana);
        batched(oneAreaWorth(ana, "91.00"));

        List<HonorRollEntry> podium = service.institutionHonorRoll(ACADEMIC_YEAR_ID,10);

        assertThat(podium).singleElement().satisfies(entry -> {
            assertThat(entry.courseId()).isEqualTo(courseId);
            assertThat(entry.gradeName()).isEqualTo("Quinto");
            assertThat(entry.parallelName()).isEqualTo("B");
        });
    }

    // ---------------------------------------------------------------- fixtures

    private void courseIs(Course course) {
        when(courseService.getById(course.id())).thenReturn(course);
    }

    private void coursesOfTheYear(Course... courses) {
        when(courseService.allOfYear(ACADEMIC_YEAR_ID)).thenReturn(List.of(courses));
    }

    private void rosterOf(CourseStudent... students) {
        rosterOfCourse(courseId, students);
    }

    private void rosterOfCourse(UUID id, CourseStudent... students) {
        when(enrollmentDomain.studentsByCourse(eq(id), any(PageQuery.class)))
            .thenReturn(new PageResult<>(List.of(students), 0, 200, students.length));
    }

    /**
     * Every score of the run, answered to whichever batch asks. The service groups them by
     * enrollment and only reads the ones on the page it is building, so rows belonging to another
     * course are simply never looked up.
     */
    private void batched(AcademicScore... scores) {
        when(scoreDomain.findByCourseEnrollmentIn(anyCollection())).thenReturn(List.of(scores));
    }

    private static AcademicScore oneAreaWorth(CourseStudent cs, String total) {
        // A single trimester is enough for the average to equal the mark: the area's annual average
        // is the mean of the trimesters it actually has.
        return score(cs.courseEnrollmentId(), UUID.randomUUID(), "Lenguaje", 1, total);
    }

    private static CourseStudent student(String names, String lastNames) {
        return new CourseStudent(UUID.randomUUID(), UUID.randomUUID(), "RUDE", "ID",
            names, lastNames, "Effective", "F");
    }

    private static Course course(UUID id, String gradeName, String parallelName) {
        return new Course(id, 5, gradeName, 2, parallelName, 1, 2026,
            UUID.randomUUID(), "Ana Perez", true);
    }

    private static AcademicScore score(UUID enrollmentId, UUID classGroupId, String subject,
                                       Integer trimester, String total) {
        return new AcademicScore(UUID.randomUUID(), enrollmentId, classGroupId, subject, trimester,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            total == null ? null : new BigDecimal(total), null, null);
    }
}
