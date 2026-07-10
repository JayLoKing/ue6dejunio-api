package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyBatchResult;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IAttendanceService {
    Attendance registerDaily(UUID courseEnrollmentId, LocalDate date, String status);
    DailyBatchResult registerDailyBatch(LocalDate date, List<DailyMark> marks);
    Attendance registerSession(UUID courseEnrollmentId, UUID classGroupId, LocalDate date, String status);
    List<Attendance> byCourseEnrollment(UUID courseEnrollmentId);

    record DailyMark(UUID courseEnrollmentId, String status) {}
}
