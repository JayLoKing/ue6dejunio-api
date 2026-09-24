package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import java.util.List;
import java.util.UUID;

public record CourseAttendanceStats(
        UUID courseId,
        String scope,
        Integer trimester,
        AttendanceCounts overall,
        List<MonthlyAttendance> byMonth,
        List<TrimesterAttendance> byTrimester) {}
