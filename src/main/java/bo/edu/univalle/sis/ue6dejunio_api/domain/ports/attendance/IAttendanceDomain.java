package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IAttendanceDomain {
    boolean courseEnrollmentExists(UUID courseEnrollmentId);
    UUID courseOfCourseEnrollment(UUID courseEnrollmentId);
    UUID courseOfClassGroup(UUID classGroupId);
    UUID teacherOfClassGroup(UUID classGroupId);
    Attendance upsertDaily(UUID courseEnrollmentId, LocalDate date, String status);
    Attendance upsertSession(UUID courseEnrollmentId, UUID classGroupId, LocalDate date, String status);
    List<Attendance> byCourseEnrollment(UUID courseEnrollmentId);
    List<Attendance> dailyByCourseEnrollment(UUID courseEnrollmentId);
}
