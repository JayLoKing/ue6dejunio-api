package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;

import java.time.LocalDate;
import java.util.UUID;

public record AttendanceResponse(
    UUID id,
    UUID courseEnrollmentId,
    UUID classGroupId,
    LocalDate date,
    String status
) {
    public static AttendanceResponse from(Attendance a) {
        return new AttendanceResponse(a.id(), a.courseEnrollmentId(), a.classGroupId(), a.date(), a.status());
    }
}
