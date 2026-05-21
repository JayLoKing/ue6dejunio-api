package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.RegisterAttendanceCommand;

import java.util.List;
import java.util.UUID;

public interface IAttendanceDomain {
    boolean enrollmentExists(UUID enrollmentId);
    Attendance upsert(RegisterAttendanceCommand command);
    List<Attendance> findByEnrollment(UUID enrollmentId);
}
