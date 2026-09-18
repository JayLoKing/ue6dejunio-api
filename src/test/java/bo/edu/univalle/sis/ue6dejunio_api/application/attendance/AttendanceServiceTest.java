package bo.edu.univalle.sis.ue6dejunio_api.application.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.attendance.AttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
    // 2026-08-14T12:00:00 America/La_Paz — a Friday.
    private static final Clock FIXED_CLOCK =
        Clock.fixed(Instant.parse("2026-08-14T16:00:00Z"), LA_PAZ);
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 14);
    /** Monday of the same ISO week as TODAY: the earliest editable date. */
    private static final LocalDate WEEK_MONDAY = LocalDate.of(2026, 8, 10);
    /** Thursday of the same ISO week: a past school day that stays editable. */
    private static final LocalDate SAME_WEEK_THURSDAY = LocalDate.of(2026, 8, 13);
    /** Friday of the previous ISO week: outside the window, no longer editable. */
    private static final LocalDate LAST_WEEK_FRIDAY = LocalDate.of(2026, 8, 7);

    // 2026-08-16T12:00:00 America/La_Paz — a Sunday, still inside the same ISO week as TODAY.
    // Used to prove a weekend *date* is rejected even when it falls inside the editable week.
    private static final Clock SUNDAY_CLOCK =
        Clock.fixed(Instant.parse("2026-08-16T16:00:00Z"), LA_PAZ);
    private static final LocalDate SAME_WEEK_SATURDAY = LocalDate.of(2026, 8, 15);

    private static final String MSG_FUTURE = "no puede registrar asistencia de fechas futuras";
    private static final String MSG_WEEKEND = "no se registra asistencia en sabados ni domingos";
    private static final String MSG_OUT_OF_WEEK =
        "solo puede modificar registros de la semana en curso (lunes a viernes)";

    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ITrimesterPeriodDomain trimesterPeriodDomain;
    @Mock private IDomainEventPublisher events;

    private AttendanceService attendanceService;

    private void init() {
        initWith(FIXED_CLOCK);
    }

    private void initWith(Clock clock) {
        attendanceService =
            new AttendanceService(attendanceDomain, trimesterPeriodDomain, events, clock);
    }

    @Test
    void registerDaily_forToday_succeeds() {
        init();
        UUID ceId = UUID.randomUUID();
        when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);
        when(attendanceDomain.upsertDaily(ceId, TODAY, "P"))
            .thenReturn(new Attendance(UUID.randomUUID(), ceId, null, TODAY, "P"));

        Attendance r = attendanceService.registerDaily(ceId, TODAY, "P");

        assertThat(r.date()).isEqualTo(TODAY);
    }

    @Test
    void registerDaily_forEarlierSchoolDayInSameWeek_succeeds() {
        init();
        UUID ceId = UUID.randomUUID();
        when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);
        when(attendanceDomain.upsertDaily(ceId, SAME_WEEK_THURSDAY, "P"))
            .thenReturn(new Attendance(UUID.randomUUID(), ceId, null, SAME_WEEK_THURSDAY, "P"));

        Attendance r = attendanceService.registerDaily(ceId, SAME_WEEK_THURSDAY, "P");

        assertThat(r.date()).isEqualTo(SAME_WEEK_THURSDAY);
    }

    @Test
    void registerDaily_forMondayOfSameWeek_succeeds() {
        init();
        UUID ceId = UUID.randomUUID();
        when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);
        when(attendanceDomain.upsertDaily(ceId, WEEK_MONDAY, "P"))
            .thenReturn(new Attendance(UUID.randomUUID(), ceId, null, WEEK_MONDAY, "P"));

        Attendance r = attendanceService.registerDaily(ceId, WEEK_MONDAY, "P");

        assertThat(r.date()).isEqualTo(WEEK_MONDAY);
    }

    @Test
    void registerDaily_forPreviousWeek_throwsConflict() {
        init();
        UUID ceId = UUID.randomUUID();
        lenient().when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);

        assertThatThrownBy(() -> attendanceService.registerDaily(ceId, LAST_WEEK_FRIDAY, "P"))
            .isInstanceOf(ConflictException.class)
            .hasMessage(MSG_OUT_OF_WEEK);
        verify(attendanceDomain, never()).upsertDaily(ceId, LAST_WEEK_FRIDAY, "P");
    }

    @Test
    void registerDaily_forWeekendDateInsideEditableWeek_throwsConflict() {
        initWith(SUNDAY_CLOCK);
        UUID ceId = UUID.randomUUID();
        lenient().when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);

        assertThatThrownBy(() -> attendanceService.registerDaily(ceId, SAME_WEEK_SATURDAY, "P"))
            .isInstanceOf(ConflictException.class)
            .hasMessage(MSG_WEEKEND);
        verify(attendanceDomain, never()).upsertDaily(ceId, SAME_WEEK_SATURDAY, "P");
    }

    @Test
    void registerDaily_onSunday_stillAllowsFixingThatWeekFriday() {
        initWith(SUNDAY_CLOCK);
        UUID ceId = UUID.randomUUID();
        when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);
        when(attendanceDomain.upsertDaily(ceId, TODAY, "A"))
            .thenReturn(new Attendance(UUID.randomUUID(), ceId, null, TODAY, "A"));

        Attendance r = attendanceService.registerDaily(ceId, TODAY, "A");

        assertThat(r.date()).isEqualTo(TODAY);
    }

    @Test
    void registerDaily_forFutureDate_throwsConflict() {
        init();
        UUID ceId = UUID.randomUUID();
        lenient().when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);
        // Monday of the next week: future, and outside the editable window.
        LocalDate future = LocalDate.of(2026, 8, 17);

        assertThatThrownBy(() -> attendanceService.registerDaily(ceId, future, "P"))
            .isInstanceOf(ConflictException.class)
            .hasMessage(MSG_FUTURE);
        verify(attendanceDomain, never()).upsertDaily(ceId, future, "P");
    }

    @Test
    void registerDailyBatch_forToday_succeeds() {
        init();
        UUID ceId = UUID.randomUUID();
        when(attendanceDomain.existingCourseEnrollmentIds(List.of(ceId))).thenReturn(Set.of(ceId));

        var result = attendanceService.registerDailyBatch(
            TODAY, List.of(new IAttendanceService.DailyMark(ceId, "P")));

        assertThat(result.saved()).isEqualTo(1);
        verify(attendanceDomain).upsertDailyBatch(TODAY, Map.of(ceId, "P"));
    }

    @Test
    void registerDailyBatch_forEarlierSchoolDayInSameWeek_succeeds() {
        init();
        UUID ceId = UUID.randomUUID();
        when(attendanceDomain.existingCourseEnrollmentIds(List.of(ceId))).thenReturn(Set.of(ceId));

        var result = attendanceService.registerDailyBatch(
            SAME_WEEK_THURSDAY, List.of(new IAttendanceService.DailyMark(ceId, "P")));

        assertThat(result.saved()).isEqualTo(1);
        verify(attendanceDomain).upsertDailyBatch(SAME_WEEK_THURSDAY, Map.of(ceId, "P"));
    }

    @Test
    void registerDailyBatch_forPreviousWeek_throwsConflict() {
        init();
        UUID ceId = UUID.randomUUID();

        assertThatThrownBy(() -> attendanceService.registerDailyBatch(
            LAST_WEEK_FRIDAY, List.of(new IAttendanceService.DailyMark(ceId, "P"))))
            .isInstanceOf(ConflictException.class)
            .hasMessage(MSG_OUT_OF_WEEK);
        verify(attendanceDomain, never()).upsertDailyBatch(LAST_WEEK_FRIDAY, Map.of(ceId, "P"));
    }

    @Test
    void registerDailyBatch_withDuplicateEnrollment_dedupesAndReportsActualSavedCount() {
        init();
        UUID ceId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        when(attendanceDomain.existingCourseEnrollmentIds(List.of(ceId, otherId, ceId)))
            .thenReturn(Set.of(ceId, otherId));

        var result = attendanceService.registerDailyBatch(TODAY, List.of(
            new IAttendanceService.DailyMark(ceId, "P"),
            new IAttendanceService.DailyMark(otherId, "A"),
            // Same courseEnrollmentId repeated: last status wins, one row is actually persisted.
            new IAttendanceService.DailyMark(ceId, "L")));

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.saved()).isEqualTo(2);
        verify(attendanceDomain).upsertDailyBatch(TODAY, Map.of(ceId, "L", otherId, "A"));
    }

    @Test
    void registerDailyBatch_withOneMissingEnrollment_throwsNotFoundAndSavesNothing() {
        init();
        UUID okId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        // Only okId is reported back by the single bounded existence check — one query for the
        // whole batch instead of one per mark.
        when(attendanceDomain.existingCourseEnrollmentIds(List.of(okId, missingId)))
            .thenReturn(Set.of(okId));

        assertThatThrownBy(() -> attendanceService.registerDailyBatch(TODAY, List.of(
            new IAttendanceService.DailyMark(okId, "P"),
            new IAttendanceService.DailyMark(missingId, "A"))))
            .isInstanceOf(bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException.class);

        // Whole batch fails atomically: nothing gets persisted, not even the valid mark.
        verify(attendanceDomain, never()).upsertDailyBatch(org.mockito.ArgumentMatchers.eq(TODAY), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void registerSession_forToday_succeeds() {
        init();
        UUID ceId = UUID.randomUUID();
        UUID cgId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);
        when(attendanceDomain.courseOfCourseEnrollment(ceId)).thenReturn(courseId);
        when(attendanceDomain.courseOfClassGroup(cgId)).thenReturn(courseId);
        when(attendanceDomain.upsertSession(ceId, cgId, TODAY, "P"))
            .thenReturn(new Attendance(UUID.randomUUID(), ceId, cgId, TODAY, "P"));

        Attendance r = attendanceService.registerSession(ceId, cgId, TODAY, "P");

        assertThat(r.date()).isEqualTo(TODAY);
    }

    @Test
    void registerSession_forEarlierSchoolDayInSameWeek_succeeds() {
        init();
        UUID ceId = UUID.randomUUID();
        UUID cgId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);
        when(attendanceDomain.courseOfCourseEnrollment(ceId)).thenReturn(courseId);
        when(attendanceDomain.courseOfClassGroup(cgId)).thenReturn(courseId);
        when(attendanceDomain.upsertSession(ceId, cgId, SAME_WEEK_THURSDAY, "P"))
            .thenReturn(new Attendance(UUID.randomUUID(), ceId, cgId, SAME_WEEK_THURSDAY, "P"));

        Attendance r = attendanceService.registerSession(ceId, cgId, SAME_WEEK_THURSDAY, "P");

        assertThat(r.date()).isEqualTo(SAME_WEEK_THURSDAY);
    }

    @Test
    void registerSessionBatch_forToday_savesWholeGroupInOneCall() {
        init();
        UUID cgId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID ceA = UUID.randomUUID();
        UUID ceB = UUID.randomUUID();
        when(attendanceDomain.courseOfClassGroup(cgId)).thenReturn(courseId);
        when(attendanceDomain.existingCourseEnrollmentIds(List.of(ceA, ceB)))
            .thenReturn(Set.of(ceA, ceB));
        when(attendanceDomain.courseEnrollmentIdsInCourse(List.of(ceA, ceB), courseId))
            .thenReturn(Set.of(ceA, ceB));

        var result = attendanceService.registerSessionBatch(cgId, TODAY, List.of(
            new IAttendanceService.DailyMark(ceA, "Present"),
            new IAttendanceService.DailyMark(ceB, "Absent")));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.saved()).isEqualTo(2);
        verify(attendanceDomain).upsertSessionBatch(cgId, TODAY,
            Map.of(ceA, "Present", ceB, "Absent"));
    }

    @Test
    void registerSessionBatch_withDuplicateEnrollment_dedupesAndReportsActualSavedCount() {
        init();
        UUID cgId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID ceA = UUID.randomUUID();
        when(attendanceDomain.courseOfClassGroup(cgId)).thenReturn(courseId);
        when(attendanceDomain.existingCourseEnrollmentIds(List.of(ceA, ceA)))
            .thenReturn(Set.of(ceA));
        when(attendanceDomain.courseEnrollmentIdsInCourse(List.of(ceA, ceA), courseId))
            .thenReturn(Set.of(ceA));

        var result = attendanceService.registerSessionBatch(cgId, TODAY, List.of(
            new IAttendanceService.DailyMark(ceA, "Present"),
            // Last status wins, exactly as in the daily batch.
            new IAttendanceService.DailyMark(ceA, "Late")));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.saved()).isEqualTo(1);
        verify(attendanceDomain).upsertSessionBatch(cgId, TODAY, Map.of(ceA, "Late"));
    }

    @Test
    void registerSessionBatch_forPreviousWeek_throwsConflict() {
        init();
        UUID cgId = UUID.randomUUID();
        UUID ceA = UUID.randomUUID();

        assertThatThrownBy(() -> attendanceService.registerSessionBatch(cgId, LAST_WEEK_FRIDAY,
            List.of(new IAttendanceService.DailyMark(ceA, "Present"))))
            .isInstanceOf(ConflictException.class)
            .hasMessage(MSG_OUT_OF_WEEK);
        verify(attendanceDomain, never()).upsertSessionBatch(any(), any(), anyMap());
    }

    @Test
    void registerSessionBatch_withOneMissingEnrollment_throwsNotFoundAndSavesNothing() {
        init();
        UUID cgId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID ceOk = UUID.randomUUID();
        UUID ceMissing = UUID.randomUUID();
        when(attendanceDomain.courseOfClassGroup(cgId)).thenReturn(courseId);
        when(attendanceDomain.existingCourseEnrollmentIds(List.of(ceOk, ceMissing)))
            .thenReturn(Set.of(ceOk));

        assertThatThrownBy(() -> attendanceService.registerSessionBatch(cgId, TODAY, List.of(
            new IAttendanceService.DailyMark(ceOk, "Present"),
            new IAttendanceService.DailyMark(ceMissing, "Absent"))))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(attendanceDomain, never()).upsertSessionBatch(any(), any(), anyMap());
    }

    @Test
    void registerSessionBatch_withEnrollmentFromAnotherCourse_throwsConflictAndSavesNothing() {
        init();
        UUID cgId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID ceOwn = UUID.randomUUID();
        UUID ceForeign = UUID.randomUUID();
        when(attendanceDomain.courseOfClassGroup(cgId)).thenReturn(courseId);
        when(attendanceDomain.existingCourseEnrollmentIds(List.of(ceOwn, ceForeign)))
            .thenReturn(Set.of(ceOwn, ceForeign));
        // Both enrollments exist, but only one belongs to the class group's course.
        when(attendanceDomain.courseEnrollmentIdsInCourse(List.of(ceOwn, ceForeign), courseId))
            .thenReturn(Set.of(ceOwn));

        assertThatThrownBy(() -> attendanceService.registerSessionBatch(cgId, TODAY, List.of(
            new IAttendanceService.DailyMark(ceOwn, "Present"),
            new IAttendanceService.DailyMark(ceForeign, "Absent"))))
            .isInstanceOf(ConflictException.class)
            .hasMessage("El estudiante no pertenece al curso de la materia");

        verify(attendanceDomain, never()).upsertSessionBatch(any(), any(), anyMap());
    }

    @Test
    void registerSession_forPreviousWeek_throwsConflict() {
        init();
        UUID ceId = UUID.randomUUID();
        UUID cgId = UUID.randomUUID();

        assertThatThrownBy(() -> attendanceService.registerSession(ceId, cgId, LAST_WEEK_FRIDAY, "P"))
            .isInstanceOf(ConflictException.class)
            .hasMessage(MSG_OUT_OF_WEEK);
        verify(attendanceDomain, never()).upsertSession(ceId, cgId, LAST_WEEK_FRIDAY, "P");
    }
}
