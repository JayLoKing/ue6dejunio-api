package bo.edu.univalle.sis.ue6dejunio_api.application.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook.GradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseOverview;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit (no DB) verification that courseOverview composes course header + classGroups +
 * the existing batch-loaded centralizer path verbatim, with no per-student score lookup.
 */
@ExtendWith(MockitoExtension.class)
class GradebookServiceCourseOverviewTest {

    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IScoreDomain scoreDomain;
    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ICourseService courseService;
    @Mock private IClassGroupDomain classGroupDomain;

    private GradebookService service;

    private UUID courseId;
    private UUID enrollmentA;
    private UUID studentA;
    private UUID classGroupMath;
    private PageQuery pageQuery;

    @BeforeEach
    void setUp() {
        service = new GradebookService(enrollmentDomain, scoreDomain, attendanceDomain,
            courseService, classGroupDomain);
        courseId = UUID.randomUUID();
        enrollmentA = UUID.randomUUID();
        studentA = UUID.randomUUID();
        classGroupMath = UUID.randomUUID();
        pageQuery = PageQuery.of(0, 30);
    }

    private CourseStudent courseStudent(UUID enrollmentId, UUID studentId, String names, String lastNames) {
        return new CourseStudent(enrollmentId, studentId, "RUDE", "ID", names, lastNames, "Effective", "F");
    }

    @Test
    void courseOverview_composesHeaderClassGroupsAndBatchedStudents() {
        Course course = new Course(courseId, 1, "Primero", 1, "A", 1, 2026,
            UUID.randomUUID(), "Ana", true);
        when(courseService.getById(courseId)).thenReturn(course);

        List<ClassGroup> classGroups = List.of(
            new ClassGroup(UUID.randomUUID(), courseId, "Primero", "A", UUID.randomUUID(),
                "Matematicas", UUID.randomUUID(), "Prof. Lopez", true));
        when(classGroupDomain.byCourse(courseId)).thenReturn(classGroups);

        PageResult<CourseStudent> page = page(
            List.of(courseStudent(enrollmentA, studentA, "Ana", "Perez")));
        when(enrollmentDomain.studentsByCourse(courseId, pageQuery)).thenReturn(page);
        List<AcademicScore> batched = List.of(
            new AcademicScore(UUID.randomUUID(), enrollmentA, classGroupMath, "Matematicas", 1,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("90.00"), null, null));
        when(scoreDomain.findByCourseEnrollmentIn(anyCollection())).thenReturn(batched);

        CourseOverview overview = service.courseOverview(courseId, 1, pageQuery);

        assertThat(overview.course()).isEqualTo(course);
        assertThat(overview.classGroups()).isEqualTo(classGroups);
        PageResult<StudentTrimesterSummary> students = overview.students();
        assertThat(students.content()).hasSize(1);
        assertThat(students.content().get(0).generalAverage()).isEqualByComparingTo("90.00");

        // N+1 guard: batch path used, never a per-enrollment lookup.
        verify(scoreDomain, never()).findByCourseEnrollment(any());
    }

    @Test
    void courseOverview_unknownCourse_propagatesNotFound() {
        when(courseService.getById(courseId))
            .thenThrow(new ResourceNotFoundException("Course", courseId));

        assertThatThrownBy(() -> service.courseOverview(courseId, 1, pageQuery))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(classGroupDomain, never()).byCourse(any());
        verify(enrollmentDomain, never()).studentsByCourse(any(), any());
    }

    @Test
    void courseOverview_emptyCourse_returnsEmptyStudentPage() {
        Course course = new Course(courseId, 1, "Primero", 1, "A", 1, 2026,
            UUID.randomUUID(), "Ana", true);
        when(courseService.getById(courseId)).thenReturn(course);
        when(classGroupDomain.byCourse(courseId)).thenReturn(List.of());
        PageResult<CourseStudent> emptyPage = page(List.of());
        when(enrollmentDomain.studentsByCourse(courseId, pageQuery)).thenReturn(emptyPage);

        CourseOverview overview = service.courseOverview(courseId, 1, pageQuery);

        assertThat(overview.students().content()).isEmpty();
        assertThat(overview.students().totalElements()).isZero();
        verify(scoreDomain, never()).findByCourseEnrollmentIn(anyCollection());
    }
    /** One full page of these rows, the shape a domain port returns. */
    private static <T> PageResult<T> page(List<T> rows) {
        return new PageResult<>(rows, 0, 30, rows.size());
    }
}
