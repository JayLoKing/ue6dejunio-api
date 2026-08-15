package bo.edu.univalle.sis.ue6dejunio_api.application.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.attendance.AttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");
    // 2026-08-14T12:00:00 America/La_Paz
    private static final Clock FIXED_CLOCK =
        Clock.fixed(Instant.parse("2026-08-14T16:00:00Z"), LA_PAZ);
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 14);

    @Mock private IAttendanceDomain attendanceDomain;
    @Mock private ITrimesterPeriodDomain trimesterPeriodDomain;

    private AttendanceService attendanceService;

    private void init() {
        attendanceService = new AttendanceService(attendanceDomain, trimesterPeriodDomain, FIXED_CLOCK);
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
    void registerDaily_forPastDate_throwsConflict() {
        init();
        UUID ceId = UUID.randomUUID();
        lenient().when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);
        LocalDate past = TODAY.minusDays(1);

        assertThatThrownBy(() -> attendanceService.registerDaily(ceId, past, "P"))
            .isInstanceOf(ConflictException.class)
            .hasMessage("no puede modificar registros de dias anteriores");
        verify(attendanceDomain, never()).upsertDaily(ceId, past, "P");
    }

    @Test
    void registerDaily_forFutureDate_throwsConflict() {
        init();
        UUID ceId = UUID.randomUUID();
        lenient().when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);
        LocalDate future = TODAY.plusDays(1);

        assertThatThrownBy(() -> attendanceService.registerDaily(ceId, future, "P"))
            .isInstanceOf(ConflictException.class)
            .hasMessage("no puede modificar registros de dias anteriores");
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
    void registerDailyBatch_forPastDate_throwsConflict() {
        init();
        UUID ceId = UUID.randomUUID();
        LocalDate past = TODAY.minusDays(1);

        assertThatThrownBy(() -> attendanceService.registerDailyBatch(
            past, List.of(new IAttendanceService.DailyMark(ceId, "P"))))
            .isInstanceOf(ConflictException.class)
            .hasMessage("no puede modificar registros de dias anteriores");
        verify(attendanceDomain, never()).upsertDailyBatch(past, Map.of(ceId, "P"));
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
    void registerSession_forPastDate_throwsConflict() {
        init();
        UUID ceId = UUID.randomUUID();
        UUID cgId = UUID.randomUUID();
        LocalDate past = TODAY.minusDays(1);

        assertThatThrownBy(() -> attendanceService.registerSession(ceId, cgId, past, "P"))
            .isInstanceOf(ConflictException.class)
            .hasMessage("no puede modificar registros de dias anteriores");
        verify(attendanceDomain, never()).upsertSession(ceId, cgId, past, "P");
    }
}
