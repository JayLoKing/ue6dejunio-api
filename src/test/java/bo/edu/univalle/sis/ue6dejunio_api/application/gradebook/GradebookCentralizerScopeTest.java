package bo.edu.univalle.sis.ue6dejunio_api.application.gradebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook.GradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualSubjectScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentAnnualSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentTrimesterSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.SubjectScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Which subjects the two centralizers show a reader. Staff and the homeroom teacher get the sheet
 * as it is today; any other teacher of the course gets only the class groups they teach, and none
 * of the averages taken across every subject — a column of their own subject's marks, not a peek at
 * the rest of the course.
 */
@ExtendWith(MockitoExtension.class)
class GradebookCentralizerScopeTest {

    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IScoreDomain scoreDomain;
    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ICourseService courseService;
    @Mock private IClassGroupDomain classGroupDomain;

    private GradebookService service;

    private final UUID courseId = UUID.randomUUID();
    private final UUID homeroom = UUID.randomUUID();
    private final UUID technical = UUID.randomUUID();
    private final UUID mathGroup = UUID.randomUUID();
    private final UUID languageGroup = UUID.randomUUID();
    private final UUID enrollmentA = UUID.randomUUID();
    private final UUID enrollmentB = UUID.randomUUID();
    private final PageQuery pageQuery = PageQuery.of(0, 30);

    @BeforeEach
    void setUp() {
        service =
                new GradebookService(
                        enrollmentDomain,
                        scoreDomain,
                        attendanceDomain,
                        courseService,
                        classGroupDomain);
        when(enrollmentDomain.studentsByCourse(courseId, pageQuery))
                .thenReturn(
                        new PageResult<>(
                                List.of(
                                        courseStudent(enrollmentA, "Ana", "Alvarez"),
                                        courseStudent(enrollmentB, "Beto", "Benitez")),
                                0,
                                30,
                                2));
        when(scoreDomain.findByCourseEnrollmentIn(anyCollection()))
                .thenReturn(
                        List.of(
                                score(enrollmentA, mathGroup, "Matematicas", "90.00"),
                                score(enrollmentA, languageGroup, "Lenguaje", "45.00"),
                                score(enrollmentB, mathGroup, "Matematicas", "60.00"),
                                score(enrollmentB, languageGroup, "Lenguaje", "80.00")));
    }

    @Test
    void centralizer_noScope_staffReadsTheWholeSheetWithoutAnyOwnershipLookup() {
        StudentTrimesterSummary row =
                service.centralizer(courseId, 1, pageQuery, null).content().get(0);

        assertThat(row.subjects()).hasSize(2);
        assertThat(row.generalAverage()).isEqualByComparingTo("67.50");
        verifyNoInteractions(classGroupDomain, courseService);
    }

    @Test
    void centralizer_homeroomTeacher_readsTheWholeSheet() {
        courseHasHomeroom(homeroom);

        StudentTrimesterSummary row =
                service.centralizer(courseId, 1, pageQuery, homeroom).content().get(0);

        assertThat(row.subjects()).hasSize(2);
        assertThat(row.generalAverage()).isEqualByComparingTo("67.50");
        verify(classGroupDomain, never()).classGroupIdsTaughtInCourse(homeroom, courseId);
    }

    @Test
    void centralizer_technicalTeacher_readsOnlyTheirSubjectAndNoGeneralAverage() {
        courseHasHomeroom(homeroom);
        when(classGroupDomain.classGroupIdsTaughtInCourse(technical, courseId))
                .thenReturn(Set.of(languageGroup));

        List<StudentTrimesterSummary> rows =
                service.centralizer(courseId, 1, pageQuery, technical).content();

        assertThat(rows).hasSize(2);
        assertThat(rows)
                .allSatisfy(
                        row -> {
                            assertThat(row.subjects())
                                    .extracting(SubjectScore::classGroupId)
                                    .containsExactly(languageGroup);
                            assertThat(row.generalAverage()).isNull();
                        });
        // One ownership lookup for the whole page, never one per student.
        verify(classGroupDomain, times(1)).classGroupIdsTaughtInCourse(technical, courseId);
    }

    @Test
    void centralizer_courseWithNoHomeroom_doesNotMakeEveryTeacherAHomeroom() {
        courseHasHomeroom(null);
        when(classGroupDomain.classGroupIdsTaughtInCourse(technical, courseId))
                .thenReturn(Set.of(languageGroup));

        StudentTrimesterSummary row =
                service.centralizer(courseId, 1, pageQuery, technical).content().get(0);

        assertThat(row.subjects()).hasSize(1);
    }

    @Test
    void annualCentralizer_homeroomTeacher_readsTheWholeYear() {
        courseHasHomeroom(homeroom);

        StudentAnnualSummary row =
                service.annualCentralizer(courseId, pageQuery, homeroom).content().get(0);

        assertThat(row.subjects()).hasSize(2);
        assertThat(row.trimester1Average()).isEqualByComparingTo("67.50");
        assertThat(row.finalAverage()).isEqualByComparingTo("67.50");
    }

    @Test
    void annualCentralizer_technicalTeacher_readsOnlyTheirAreaAndNoAverageAcrossSubjects() {
        courseHasHomeroom(homeroom);
        when(classGroupDomain.classGroupIdsTaughtInCourse(technical, courseId))
                .thenReturn(Set.of(languageGroup));

        StudentAnnualSummary row =
                service.annualCentralizer(courseId, pageQuery, technical).content().get(0);

        assertThat(row.subjects())
                .extracting(AnnualSubjectScore::classGroupId)
                .containsExactly(languageGroup);
        // Their own area keeps its marks and its own average; only the cross-subject columns go.
        assertThat(row.subjects().get(0).trimester1()).isEqualByComparingTo("45.00");
        assertThat(row.subjects().get(0).average()).isEqualByComparingTo("45.00");
        assertThat(row.trimester1Average()).isNull();
        assertThat(row.trimester2Average()).isNull();
        assertThat(row.trimester3Average()).isNull();
        assertThat(row.finalAverage()).isNull();
        assertThat(row.finalResult()).isNull();
    }

    private void courseHasHomeroom(UUID homeroomTeacherId) {
        when(courseService.getById(courseId))
                .thenReturn(
                        new Course(
                                courseId,
                                1,
                                "Quinto",
                                1,
                                "A",
                                1,
                                2026,
                                homeroomTeacherId,
                                "X",
                                true,
                                true));
    }

    private static CourseStudent courseStudent(UUID enrollmentId, String names, String lastNames) {
        return new CourseStudent(
                enrollmentId, UUID.randomUUID(), "RUDE", "ID", names, lastNames, "Effective", "F");
    }

    private static AcademicScore score(
            UUID enrollmentId, UUID classGroupId, String subject, String total) {
        return new AcademicScore(
                UUID.randomUUID(),
                enrollmentId,
                classGroupId,
                subject,
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal(total),
                null,
                null);
    }
}
