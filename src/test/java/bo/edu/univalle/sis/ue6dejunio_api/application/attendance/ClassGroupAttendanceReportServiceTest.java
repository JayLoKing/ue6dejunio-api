package bo.edu.univalle.sis.ue6dejunio_api.application.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.attendance.AttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.CourseStudentAttendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.EnrollmentStatusCount;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.StudentAttendanceSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodDomain;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The per-student attendance report of one class group: a technical teacher's own subject.
 *
 * <p>It is the course report of RF 37 with one thing swapped — the marks counted are that class
 * group's session rows instead of the daily ones. The roll, the period filter and the percentage
 * rule are the course report's own, so these tests check that the swap is the only difference.
 */
@ExtendWith(MockitoExtension.class)
class ClassGroupAttendanceReportServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-14T16:00:00Z"), ZoneOffset.UTC);
    private static final Integer YEAR_ID = 7;
    private static final PageQuery WIDE = PageQuery.of(0, 200);

    /** Trimestre 1 = febrero a mayo; trimestre 2 = junio a agosto. */
    private static final List<TrimesterPeriod> PERIODS =
            List.of(
                    new TrimesterPeriod(
                            UUID.randomUUID(),
                            YEAR_ID,
                            1,
                            LocalDate.parse("2026-02-01"),
                            LocalDate.parse("2026-05-31")),
                    new TrimesterPeriod(
                            UUID.randomUUID(),
                            YEAR_ID,
                            2,
                            LocalDate.parse("2026-06-01"),
                            LocalDate.parse("2026-08-31")));

    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ITrimesterPeriodDomain trimesterPeriodDomain;
    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IDomainEventPublisher events;

    private AttendanceService service;
    private final UUID courseId = UUID.randomUUID();
    private final UUID classGroupId = UUID.randomUUID();
    private final UUID anaEnrollment = UUID.randomUUID();
    private final UUID luisEnrollment = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new AttendanceService(
                        attendanceDomain,
                        trimesterPeriodDomain,
                        enrollmentDomain,
                        events,
                        mock(ClosedYearGuard.class),
                        FIXED_CLOCK);
        lenient().when(attendanceDomain.courseOfClassGroup(classGroupId)).thenReturn(courseId);
        lenient().when(attendanceDomain.courseExists(courseId)).thenReturn(true);
        lenient().when(attendanceDomain.academicYearOfCourse(courseId)).thenReturn(YEAR_ID);
        lenient().when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(PERIODS);
    }

    private void roster(CourseStudent... students) {
        when(enrollmentDomain.activeStudentsByCourse(courseId, WIDE))
                .thenReturn(new PageResult<>(List.of(students), 0, 200, students.length));
    }

    private static CourseStudent student(UUID enrollmentId, String names, String lastNames) {
        return new CourseStudent(
                enrollmentId, UUID.randomUUID(), "RUDE", "CI", names, lastNames, "Effective", "F");
    }

    private static EnrollmentStatusCount mark(UUID enrollment, String date, String status) {
        return new EnrollmentStatusCount(enrollment, LocalDate.parse(date), status, 1);
    }

    private static StudentAttendanceSummary summaryOf(
            CourseStudentAttendance report, UUID courseEnrollmentId) {
        return report.students().content().stream()
                .filter(s -> s.courseEnrollmentId().equals(courseEnrollmentId))
                .findFirst()
                .orElseThrow();
    }

    /** 3 present + 1 late attended over 6 computable sessions; the excused one is a falta. */
    @Test
    void appliesTheCourseReportsRuleToTheClassGroupsSessionMarks() {
        roster(student(anaEnrollment, "Ana", "Quispe"));
        when(attendanceDomain.sessionStatusCountsByEnrollmentIn(
                        classGroupId, List.of(anaEnrollment)))
                .thenReturn(
                        List.of(
                                mark(anaEnrollment, "2026-03-02", "Present"),
                                mark(anaEnrollment, "2026-03-03", "Present"),
                                mark(anaEnrollment, "2026-03-04", "Present"),
                                mark(anaEnrollment, "2026-03-05", "Absent"),
                                mark(anaEnrollment, "2026-03-06", "Late"),
                                mark(anaEnrollment, "2026-03-09", "Excused")));

        CourseStudentAttendance report =
                service.attendanceByStudentOfClassGroup(classGroupId, 1, WIDE);

        StudentAttendanceSummary ana = summaryOf(report, anaEnrollment);
        assertThat(ana.counts().present()).isEqualTo(3);
        assertThat(ana.counts().excused()).isEqualTo(1);
        assertThat(ana.counts().computableSessions()).isEqualTo(6);
        assertThat(ana.counts().percentage()).isEqualByComparingTo("66.7");
        // The report names the course the class group belongs to, like the course report does.
        assertThat(report.courseId()).isEqualTo(courseId);
    }

    /** The daily rows are the homeroom teacher's; this report never asks for them. */
    @Test
    void neverReadsTheDailyRows() {
        roster(student(anaEnrollment, "Ana", "Quispe"));
        when(attendanceDomain.sessionStatusCountsByEnrollmentIn(
                        classGroupId, List.of(anaEnrollment)))
                .thenReturn(List.of());

        service.attendanceByStudentOfClassGroup(classGroupId, 1, WIDE);

        verify(attendanceDomain, never()).dailyStatusCountsByEnrollmentIn(anyCollection());
    }

    @Test
    void keepsAStudentNobodyMarkedAtZeroWithANullPercentage() {
        roster(student(anaEnrollment, "Ana", "Quispe"), student(luisEnrollment, "Luis", "Mamani"));
        when(attendanceDomain.sessionStatusCountsByEnrollmentIn(
                        classGroupId, List.of(anaEnrollment, luisEnrollment)))
                .thenReturn(List.of(mark(anaEnrollment, "2026-03-02", "Present")));

        CourseStudentAttendance report =
                service.attendanceByStudentOfClassGroup(classGroupId, 1, WIDE);

        StudentAttendanceSummary luis = summaryOf(report, luisEnrollment);
        assertThat(luis.counts().computableSessions()).isZero();
        assertThat(luis.counts().percentage()).isNull();
    }

    /** The course report's period filter, not one of its own. */
    @Test
    void excludesSessionsOutsideTheRequestedTrimester() {
        roster(student(anaEnrollment, "Ana", "Quispe"));
        when(attendanceDomain.sessionStatusCountsByEnrollmentIn(
                        classGroupId, List.of(anaEnrollment)))
                .thenReturn(
                        List.of(
                                mark(anaEnrollment, "2026-03-02", "Present"),
                                mark(anaEnrollment, "2026-07-02", "Absent"),
                                mark(anaEnrollment, "2026-12-20", "Absent")));

        CourseStudentAttendance report =
                service.attendanceByStudentOfClassGroup(classGroupId, 1, WIDE);

        assertThat(summaryOf(report, anaEnrollment).counts().computableSessions()).isEqualTo(1);
        assertThat(report.scope()).isEqualTo("trimester");
    }

    @Test
    void withoutTrimester_coversEveryConfiguredPeriod() {
        roster(student(anaEnrollment, "Ana", "Quispe"));
        when(attendanceDomain.sessionStatusCountsByEnrollmentIn(
                        classGroupId, List.of(anaEnrollment)))
                .thenReturn(
                        List.of(
                                mark(anaEnrollment, "2026-03-02", "Present"),
                                mark(anaEnrollment, "2026-07-02", "Present"),
                                mark(anaEnrollment, "2026-12-20", "Absent")));

        CourseStudentAttendance report =
                service.attendanceByStudentOfClassGroup(classGroupId, null, WIDE);

        assertThat(summaryOf(report, anaEnrollment).counts().computableSessions()).isEqualTo(2);
        assertThat(report.scope()).isEqualTo("annual");
        assertThat(report.trimester()).isNull();
    }

    @Test
    void unknownClassGroup_throwsResourceNotFound() {
        UUID missing = UUID.randomUUID();
        when(attendanceDomain.courseOfClassGroup(missing))
                .thenThrow(new ResourceNotFoundException("ClassGroup", missing));

        assertThatThrownBy(() -> service.attendanceByStudentOfClassGroup(missing, 1, WIDE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void emptyRoster_asksForNoMarks() {
        roster();

        CourseStudentAttendance report =
                service.attendanceByStudentOfClassGroup(classGroupId, 1, WIDE);

        assertThat(report.students().content()).isEmpty();
        verify(attendanceDomain, never()).sessionStatusCountsByEnrollmentIn(any(), anyCollection());
    }
}
