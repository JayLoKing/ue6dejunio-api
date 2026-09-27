package bo.edu.univalle.sis.ue6dejunio_api.application.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
 * Spec: RF 37 — the attendance percentage per student of a course, trimestral or annual.
 *
 * <p>The course-wide {@code attendanceStats} could not answer this: it groups by date and status
 * across the whole course, so it knows the classroom's percentage and not anyone's in it.
 *
 * <p>The reconciliation invariant is what these tests are really about. This report and the panel
 * read the same rows through the same period filter, so summing every student's counts here gives
 * back exactly the panel's {@code overall}. There are already three different notions of attendance
 * in this system and no room for a fourth.
 */
@ExtendWith(MockitoExtension.class)
class StudentAttendanceReportServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-14T16:00:00Z"), ZoneOffset.UTC);
    private static final Integer YEAR_ID = 7;
    private static final PageQuery WIDE = PageQuery.of(0, 200);

    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ITrimesterPeriodDomain trimesterPeriodDomain;
    @Mock private ICourseEnrollmentDomain enrollmentDomain;
    @Mock private IDomainEventPublisher events;

    private AttendanceService service;
    private UUID courseId;
    private UUID anaEnrollment;
    private UUID luisEnrollment;

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

    private CourseStudent student(UUID enrollmentId, String names, String lastNames) {
        return new CourseStudent(
                enrollmentId, UUID.randomUUID(), "RUDE", "CI", names, lastNames, "Effective", "F");
    }

    @BeforeEach
    void setUp() {
        service =
                new AttendanceService(
                        attendanceDomain,
                        trimesterPeriodDomain,
                        enrollmentDomain,
                        events,
                        FIXED_CLOCK);
        courseId = UUID.randomUUID();
        anaEnrollment = UUID.randomUUID();
        luisEnrollment = UUID.randomUUID();
        lenient().when(attendanceDomain.courseExists(courseId)).thenReturn(true);
        lenient().when(attendanceDomain.academicYearOfCourse(courseId)).thenReturn(YEAR_ID);
        lenient().when(trimesterPeriodDomain.findByAcademicYear(YEAR_ID)).thenReturn(PERIODS);
    }

    private void roster(CourseStudent... students) {
        when(enrollmentDomain.activeStudentsByCourse(courseId, WIDE))
                .thenReturn(new PageResult<>(List.of(students), 0, 200, students.length));
    }

    private static EnrollmentStatusCount mark(UUID enrollment, String date, String status) {
        return new EnrollmentStatusCount(enrollment, LocalDate.parse(date), status, 1);
    }

    private StudentAttendanceSummary summaryOf(
            CourseStudentAttendance report, UUID courseEnrollmentId) {
        return report.students().content().stream()
                .filter(s -> s.courseEnrollmentId().equals(courseEnrollmentId))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Late counts as a miss and Excused leaves the calculation entirely — the rule {@code
     * AttendanceCounts} already holds. Ana: 3 present, 1 absent, 1 late over 5 computable days is
     * 60%.
     */
    @Test
    void attendanceByStudent_appliesThePercentageRulePerStudent() {
        roster(student(anaEnrollment, "Ana", "Quispe"));
        when(attendanceDomain.dailyStatusCountsByEnrollmentIn(List.of(anaEnrollment)))
                .thenReturn(
                        List.of(
                                mark(anaEnrollment, "2026-03-02", "Present"),
                                mark(anaEnrollment, "2026-03-03", "Present"),
                                mark(anaEnrollment, "2026-03-04", "Present"),
                                mark(anaEnrollment, "2026-03-05", "Absent"),
                                mark(anaEnrollment, "2026-03-06", "Late"),
                                mark(anaEnrollment, "2026-03-09", "Excused")));

        CourseStudentAttendance report = service.attendanceByStudent(courseId, 1, WIDE);

        StudentAttendanceSummary ana = summaryOf(report, anaEnrollment);
        assertThat(ana.studentName()).isEqualTo("Ana Quispe");
        assertThat(ana.counts().present()).isEqualTo(3);
        assertThat(ana.counts().absent()).isEqualTo(1);
        assertThat(ana.counts().late()).isEqualTo(1);
        assertThat(ana.counts().excused()).isEqualTo(1);
        assertThat(ana.counts().percentage()).isEqualByComparingTo("60.0");
    }

    /** Cada estudiante cuenta lo suyo: las marcas de uno no pueden entrar en el total del otro. */
    @Test
    void attendanceByStudent_keepsEachStudentsMarksApart() {
        roster(student(anaEnrollment, "Ana", "Quispe"), student(luisEnrollment, "Luis", "Mamani"));
        when(attendanceDomain.dailyStatusCountsByEnrollmentIn(
                        List.of(anaEnrollment, luisEnrollment)))
                .thenReturn(
                        List.of(
                                mark(anaEnrollment, "2026-03-02", "Present"),
                                mark(anaEnrollment, "2026-03-03", "Present"),
                                mark(luisEnrollment, "2026-03-02", "Absent"),
                                mark(luisEnrollment, "2026-03-03", "Present")));

        CourseStudentAttendance report = service.attendanceByStudent(courseId, 1, WIDE);

        assertThat(summaryOf(report, anaEnrollment).counts().percentage())
                .isEqualByComparingTo("100.0");
        assertThat(summaryOf(report, luisEnrollment).counts().percentage())
                .isEqualByComparingTo("50.0");
    }

    /**
     * Un estudiante sin una sola marca tiene que aparecer igual, en cero y con porcentaje nulo.
     * Dejarlo afuera lo haría desaparecer del reporte justamente cuando su ausencia de registros es
     * lo que hay que ver, y un 0% inventado diría que faltó siempre.
     */
    @Test
    void attendanceByStudent_keepsAStudentWithNoMarks() {
        roster(student(luisEnrollment, "Luis", "Mamani"));
        when(attendanceDomain.dailyStatusCountsByEnrollmentIn(List.of(luisEnrollment)))
                .thenReturn(List.of());

        CourseStudentAttendance report = service.attendanceByStudent(courseId, 1, WIDE);

        StudentAttendanceSummary luis = summaryOf(report, luisEnrollment);
        assertThat(luis.counts().computableSessions()).isZero();
        assertThat(luis.counts().percentage()).isNull();
    }

    /** Fuera del trimestre pedido no cuenta, igual que en el panel del curso. */
    @Test
    void attendanceByStudent_excludesDatesOutsideTheRequestedTrimester() {
        roster(student(anaEnrollment, "Ana", "Quispe"));
        when(attendanceDomain.dailyStatusCountsByEnrollmentIn(List.of(anaEnrollment)))
                .thenReturn(
                        List.of(
                                mark(anaEnrollment, "2026-03-02", "Present"),
                                // Trimestre 2: no entra cuando se pide el 1.
                                mark(anaEnrollment, "2026-07-02", "Absent"),
                                // Vacaciones: fuera de todo período configurado.
                                mark(anaEnrollment, "2026-12-20", "Absent")));

        CourseStudentAttendance report = service.attendanceByStudent(courseId, 1, WIDE);

        StudentAttendanceSummary ana = summaryOf(report, anaEnrollment);
        assertThat(ana.counts().computableSessions()).isEqualTo(1);
        assertThat(ana.counts().percentage()).isEqualByComparingTo("100.0");
        assertThat(report.scope()).isEqualTo("trimester");
        assertThat(report.trimester()).isEqualTo(1);
    }

    /** Sin trimestre el alcance es anual: entran todos los períodos, y sólo ellos. */
    @Test
    void attendanceByStudent_withoutTrimester_coversEveryConfiguredPeriod() {
        roster(student(anaEnrollment, "Ana", "Quispe"));
        when(attendanceDomain.dailyStatusCountsByEnrollmentIn(List.of(anaEnrollment)))
                .thenReturn(
                        List.of(
                                mark(anaEnrollment, "2026-03-02", "Present"),
                                mark(anaEnrollment, "2026-07-02", "Present"),
                                mark(anaEnrollment, "2026-12-20", "Absent")));

        CourseStudentAttendance report = service.attendanceByStudent(courseId, null, WIDE);

        StudentAttendanceSummary ana = summaryOf(report, anaEnrollment);
        assertThat(ana.counts().computableSessions()).isEqualTo(2);
        assertThat(ana.counts().percentage()).isEqualByComparingTo("100.0");
        assertThat(report.scope()).isEqualTo("annual");
        assertThat(report.trimester()).isNull();
    }

    @Test
    void attendanceByStudent_unknownCourse_throwsResourceNotFound() {
        UUID missing = UUID.randomUUID();
        when(attendanceDomain.courseExists(missing)).thenReturn(false);

        assertThatThrownBy(() -> service.attendanceByStudent(missing, 1, WIDE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Sin estudiantes no hay nada que contar, y tampoco hay por qué preguntar por marcas. */
    @Test
    void attendanceByStudent_emptyRoster_asksForNoMarks() {
        roster();

        CourseStudentAttendance report = service.attendanceByStudent(courseId, 1, WIDE);

        assertThat(report.students().content()).isEmpty();
        org.mockito.Mockito.verify(attendanceDomain, org.mockito.Mockito.never())
                .dailyStatusCountsByEnrollmentIn(org.mockito.ArgumentMatchers.anyCollection());
    }
}
