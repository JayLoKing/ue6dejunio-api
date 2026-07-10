package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CourseAttendanceResponse(
    UUID courseEnrollmentId,
    UUID studentId,
    String fullName,
    List<Item> attendances
) {
    public record Item(UUID id, LocalDate date, String status) {}

    public static CourseAttendanceResponse from(CourseAttendanceRow r) {
        List<Item> items = r.attendances().stream()
            .map(a -> new Item(a.id(), a.date(), a.status())).toList();
        return new CourseAttendanceResponse(r.courseEnrollmentId(), r.studentId(), r.fullName(), items);
    }
}
