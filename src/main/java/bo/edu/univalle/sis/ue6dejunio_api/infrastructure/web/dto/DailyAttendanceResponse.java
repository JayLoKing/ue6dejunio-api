package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyAttendanceResult;

import java.util.List;
import java.util.UUID;

public record DailyAttendanceResponse(
    int studentsTotal,
    int classGroupsInCourse,
    int attendanceRowsSaved,
    List<UUID> studentsNotEnrolled
) {
    public static DailyAttendanceResponse from(DailyAttendanceResult r) {
        return new DailyAttendanceResponse(
            r.studentsTotal(), r.classGroupsInCourse(), r.attendanceRowsSaved(), r.studentsNotEnrolled());
    }
}
