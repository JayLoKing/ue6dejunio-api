package bo.edu.univalle.sis.ue6dejunio_api.application.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.gradebook.GradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit (no DB / no Testcontainers) verification of the batch-load + in-memory grouping
 * introduced to fix the centralizer/courseAttendance N+1. Confirms the batched path produces
 * output identical to the previous per-enrollment lookup path, with no cross-student bleed and
 * unchanged empty-scores shape. This is the DB-independent safety net; the byte-identical golden
 * JSON tests and the Hibernate Statistics query-count test additionally require Testcontainers
 * Postgres and are gated by {@code disabledWithoutDocker}.
 */
@ExtendWith(MockitoExtension.class)
class GradebookServiceGroupingTest {

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
    private UUID classGroupMath;
    private UUID classGroupLang;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        service = new GradebookService(enrollmentDomain, scoreDomain, attendanceDomain,
            courseService, classGroupDomain);
        courseId = UUID.randomUUID();
        enrollmentA = UUID.randomUUID();
        enrollmentB = UUID.randomUUID();
        studentA = UUID.randomUUID();
        studentB = UUID.randomUUID();
        classGroupMath = UUID.randomUUID();
        classGroupLang = UUID.randomUUID();
        pageable = PageRequest.of(0, 30);
    }

    private CourseStudent courseStudent(UUID enrollmentId, UUID studentId, String names, String lastNames) {
        return new CourseStudent(enrollmentId, studentId, "RUDE", "ID", names, lastNames, "Effective", "F");
    }

    private AcademicScore score(UUID enrollmentId, UUID classGroupId, String subject, Integer trimester,
                                BigDecimal total) {
        return new AcademicScore(UUID.randomUUID(), enrollmentId, classGroupId, subject, trimester,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, total, null, null);
    }

    @Test
    void centralizer_groupsBatchedScoresPerEnrollment_matchesPerStudentBaseline() {
        Page<CourseStudent> page = new PageImpl<>(List.of(
            courseStudent(enrollmentA, studentA, "Ana", "Perez"),
            courseStudent(enrollmentB, studentB, "Luis", "Gomez")
        ), pageable, 2);
        when(enrollmentDomain.studentsByCourse(courseId, pageable)).thenReturn(page);

        List<AcademicScore> batched = List.of(
            score(enrollmentA, classGroupLang, "Lenguaje", 1, new BigDecimal("80.00")),
            score(enrollmentA, classGroupMath, "Matematicas", 1, new BigDecimal("90.00")),
            score(enrollmentB, classGroupMath, "Matematicas", 1, new BigDecimal("70.00"))
        );
        when(scoreDomain.findByCourseEnrollmentIn(anyCollection())).thenReturn(batched);

        Page<StudentTrimesterSummary> result = service.centralizer(courseId, 1, pageable);

        assertThat(result.getContent()).hasSize(2);
        StudentTrimesterSummary rowA = result.getContent().get(0);
        StudentTrimesterSummary rowB = result.getContent().get(1);

        // No cross-student bleed: row A only has its own two subjects, row B only its one.
        assertThat(rowA.subjects()).hasSize(2);
        assertThat(rowA.subjects()).extracting("subjectName").containsExactly("Lenguaje", "Matematicas");
        assertThat(rowA.generalAverage()).isEqualByComparingTo("85.00");

        assertThat(rowB.subjects()).hasSize(1);
        assertThat(rowB.subjects()).extracting("subjectName").containsExactly("Matematicas");
        assertThat(rowB.generalAverage()).isEqualByComparingTo("70.00");

        // Batched path calls the IN-query once; never falls back to the per-enrollment lookup.
        verify(scoreDomain, never()).findByCourseEnrollment(any());
    }

    @Test
    void centralizer_emptyScoresEnrollment_keepsNullAverageShape() {
        Page<CourseStudent> page = new PageImpl<>(List.of(
            courseStudent(enrollmentA, studentA, "Ana", "Perez")
        ), pageable, 1);
        when(enrollmentDomain.studentsByCourse(courseId, pageable)).thenReturn(page);
        when(scoreDomain.findByCourseEnrollmentIn(anyCollection())).thenReturn(List.of());

        Page<StudentTrimesterSummary> result = service.centralizer(courseId, 1, pageable);

        StudentTrimesterSummary row = result.getContent().get(0);
        assertThat(row.subjects()).isEmpty();
        assertThat(row.generalAverage()).isNull();
    }

    @Test
    void centralizer_emptyPage_skipsBatchQuery() {
        Page<CourseStudent> page = new PageImpl<>(List.of(), pageable, 0);
        when(enrollmentDomain.studentsByCourse(courseId, pageable)).thenReturn(page);

        Page<StudentTrimesterSummary> result = service.centralizer(courseId, 1, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(scoreDomain, never()).findByCourseEnrollmentIn(anyCollection());
    }

    @Test
    void courseAttendance_groupsBatchedAttendancePerEnrollment_noBleed() {
        Page<CourseStudent> page = new PageImpl<>(List.of(
            courseStudent(enrollmentA, studentA, "Ana", "Perez"),
            courseStudent(enrollmentB, studentB, "Luis", "Gomez")
        ), pageable, 2);
        when(enrollmentDomain.studentsByCourse(courseId, pageable)).thenReturn(page);

        Attendance attA1 = new Attendance(UUID.randomUUID(), enrollmentA, null, LocalDate.of(2026, 3, 1), "Present");
        Attendance attA2 = new Attendance(UUID.randomUUID(), enrollmentA, null, LocalDate.of(2026, 3, 2), "Absent");
        Attendance attB1 = new Attendance(UUID.randomUUID(), enrollmentB, null, LocalDate.of(2026, 3, 1), "Present");
        when(attendanceDomain.dailyByCourseEnrollmentIn(anyCollection()))
            .thenReturn(List.of(attA1, attA2, attB1));

        Page<CourseAttendanceRow> result = service.courseAttendance(courseId, null, pageable);

        CourseAttendanceRow rowA = result.getContent().get(0);
        CourseAttendanceRow rowB = result.getContent().get(1);
        assertThat(rowA.attendances()).containsExactly(attA1, attA2);
        assertThat(rowB.attendances()).containsExactly(attB1);
        verify(attendanceDomain, never()).dailyByCourseEnrollment(any());
    }

    @Test
    void courseAttendance_emptyAttendanceEnrollment_keepsEmptyListShape() {
        Page<CourseStudent> page = new PageImpl<>(List.of(
            courseStudent(enrollmentA, studentA, "Ana", "Perez")
        ), pageable, 1);
        when(enrollmentDomain.studentsByCourse(courseId, pageable)).thenReturn(page);
        when(attendanceDomain.dailyByCourseEnrollmentIn(anyCollection())).thenReturn(List.of());

        Page<CourseAttendanceRow> result = service.courseAttendance(courseId, null, pageable);

        assertThat(result.getContent().get(0).attendances()).isEmpty();
    }

    @Test
    void courseAttendance_dateFilter_appliedAfterGrouping() {
        Page<CourseStudent> page = new PageImpl<>(List.of(
            courseStudent(enrollmentA, studentA, "Ana", "Perez")
        ), pageable, 1);
        when(enrollmentDomain.studentsByCourse(courseId, pageable)).thenReturn(page);

        Attendance attMatch = new Attendance(UUID.randomUUID(), enrollmentA, null, LocalDate.of(2026, 3, 1), "Present");
        Attendance attOther = new Attendance(UUID.randomUUID(), enrollmentA, null, LocalDate.of(2026, 3, 2), "Absent");
        when(attendanceDomain.dailyByCourseEnrollmentIn(anyCollection()))
            .thenReturn(List.of(attMatch, attOther));

        Page<CourseAttendanceRow> result =
            service.courseAttendance(courseId, LocalDate.of(2026, 3, 1), pageable);

        assertThat(result.getContent().get(0).attendances()).containsExactly(attMatch);
    }

    @Test
    void studentSummary_stillUsesSingleIdLookup_unchanged() {
        when(enrollmentDomain.courseStudentById(enrollmentA))
            .thenReturn(java.util.Optional.of(courseStudent(enrollmentA, studentA, "Ana", "Perez")));
        when(scoreDomain.findByCourseEnrollment(enrollmentA)).thenReturn(List.of(
            score(enrollmentA, classGroupMath, "Matematicas", 1, new BigDecimal("90.00"))));

        StudentTrimesterSummary result = service.studentSummary(enrollmentA, 1);

        assertThat(result.generalAverage()).isEqualByComparingTo("90.00");
        verify(scoreDomain, never()).findByCourseEnrollmentIn(anyCollection());
    }
}
