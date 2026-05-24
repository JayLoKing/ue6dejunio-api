package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.AttendanceBatchResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyAttendanceCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyAttendanceResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.RegisterAttendanceCommand;

import java.util.List;
import java.util.UUID;

public interface IAttendanceService {
    Attendance register(RegisterAttendanceCommand command);
    AttendanceBatchResult registerBatch(List<RegisterAttendanceCommand> commands);
    DailyAttendanceResult registerDaily(DailyAttendanceCommand command);
    List<Attendance> byEnrollment(UUID enrollmentId);
}
