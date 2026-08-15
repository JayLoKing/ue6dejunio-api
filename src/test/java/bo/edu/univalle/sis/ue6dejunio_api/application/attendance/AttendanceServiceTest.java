package bo.edu.univalle.sis.ue6dejunio_api.application.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.attendance.AttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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

    private AttendanceService attendanceService;

    private void init() {
        attendanceService = new AttendanceService(attendanceDomain, FIXED_CLOCK);
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
        when(attendanceDomain.courseEnrollmentExists(ceId)).thenReturn(true);

        var result = attendanceService.registerDailyBatch(
            TODAY, List.of(new IAttendanceService.DailyMark(ceId, "P")));

        assertThat(result.saved()).isEqualTo(1);
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
        verify(attendanceDomain, never()).upsertDaily(ceId, past, "P");
    }
}
