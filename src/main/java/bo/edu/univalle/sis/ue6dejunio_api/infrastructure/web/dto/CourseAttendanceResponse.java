package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.CourseAttendanceRow;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CourseAttendanceResponse(
    UUID studentId,
    String fullName,
    UUID enrollmentId,
    List<Item> attendances
) {
    public record Item(UUID id, LocalDate date, String status) {}

    public static CourseAttendanceResponse from(CourseAttendanceRow r) {
        List<Item> items = r.attendances().stream()
            .map(a -> new Item(a.id(), a.date(), a.status()))
            .toList();
        return new CourseAttendanceResponse(r.studentId(), r.fullName(), r.enrollmentId(), items);
    }
}
