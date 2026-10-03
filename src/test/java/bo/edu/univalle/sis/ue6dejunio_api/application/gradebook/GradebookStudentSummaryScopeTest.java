package bo.edu.univalle.sis.ue6dejunio_api.application.gradebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook.GradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Which subjects of a student's trimester summary a reader gets: all of them for the staff and the
 * homeroom teacher, only their own for any other teacher — with the general average left as the
 * school computes it, never recomputed from the narrowed rows.
 */
@ExtendWith(MockitoExtension.class)
class GradebookStudentSummaryScopeTest {

    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IScoreDomain scoreDomain;
    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ICourseService courseService;
    @Mock private IClassGroupDomain classGroupDomain;

    private GradebookService service;

    private final UUID courseId = UUID.randomUUID();
    private final UUID enrollment = UUID.randomUUID();
    private final UUID homeroom = UUID.randomUUID();
    private final UUID technical = UUID.randomUUID();
    private final UUID mathGroup = UUID.randomUUID();
    private final UUID languageGroup = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new GradebookService(
                        enrollmentDomain,
                        scoreDomain,
                        attendanceDomain,
                        courseService,
                        classGroupDomain);
        when(enrollmentDomain.courseStudentById(enrollment))
                .thenReturn(
                        Optional.of(
                                new CourseStudent(
                                        enrollment,
                                        UUID.randomUUID(),
                                        "RUDE",
                                        "ID",
                                        "Ana",
                                        "Alvarez",
                                        "Effective",
                                        "F")));
        when(scoreDomain.findByCourseEnrollment(enrollment))
                .thenReturn(
                        List.of(
                                score(mathGroup, "Matematicas", "90.00"),
                                score(languageGroup, "Lenguaje", "45.00")));
    }

    private AcademicScore score(UUID classGroupId, String subject, String total) {
        return new AcademicScore(
                UUID.randomUUID(),
                enrollment,
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

    private void courseHasHomeroom(UUID homeroomTeacherId) {
        when(enrollmentDomain.courseOfEnrollment(enrollment)).thenReturn(courseId);
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

    @Test
    void noScope_staffReadsEverySubjectWithoutAnyOwnershipLookup() {
        StudentTrimesterSummary summary = service.studentSummary(enrollment, 1, null);

        assertThat(summary.subjects())
                .extracting(SubjectScore::classGroupId)
                .containsExactly(mathGroup, languageGroup);
        verifyNoInteractions(classGroupDomain, courseService);
    }

    @Test
    void homeroomTeacher_readsEverySubject() {
        courseHasHomeroom(homeroom);

        StudentTrimesterSummary summary = service.studentSummary(enrollment, 1, homeroom);

        assertThat(summary.subjects()).hasSize(2);
        verify(classGroupDomain, never()).classGroupIdsTaughtInCourse(homeroom, courseId);
    }

    @Test
    void otherTeacher_readsOnlyTheirSubjects_andTheGeneralAverageOfTheWholeStudent() {
        courseHasHomeroom(homeroom);
        when(classGroupDomain.classGroupIdsTaughtInCourse(technical, courseId))
                .thenReturn(Set.of(languageGroup));

        StudentTrimesterSummary summary = service.studentSummary(enrollment, 1, technical);

        assertThat(summary.subjects())
                .extracting(SubjectScore::classGroupId)
                .containsExactly(languageGroup);
        // The mean of 90 and 45, not 45: the narrowing must not leak into the average.
        assertThat(summary.generalAverage()).isEqualByComparingTo("67.50");
    }

    @Test
    void courseWithNoHomeroom_doesNotMakeEveryTeacherAHomeroom() {
        courseHasHomeroom(null);
        when(classGroupDomain.classGroupIdsTaughtInCourse(technical, courseId))
                .thenReturn(Set.of(languageGroup));

        assertThat(service.studentSummary(enrollment, 1, technical).subjects()).hasSize(1);
    }
}
