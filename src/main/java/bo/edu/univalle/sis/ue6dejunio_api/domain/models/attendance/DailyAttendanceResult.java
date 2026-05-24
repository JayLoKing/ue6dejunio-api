package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import java.util.List;
import java.util.UUID;

public record DailyAttendanceResult(
    int studentsTotal,
    int classGroupsInCourse,
    int attendanceRowsSaved,
    List<UUID> studentsNotEnrolled
) {}
