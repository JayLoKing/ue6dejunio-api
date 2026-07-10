package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;

import java.util.List;
import java.util.UUID;

public record CourseAttendanceRow(
    UUID courseEnrollmentId,
    UUID studentId,
    String fullName,
    List<Attendance> attendances
) {}
