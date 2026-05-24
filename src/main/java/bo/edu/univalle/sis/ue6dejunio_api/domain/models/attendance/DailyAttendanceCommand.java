package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DailyAttendanceCommand(
    Integer gradeId,
    Integer parallelId,
    LocalDate date,
    List<StudentMark> records
) {
    public record StudentMark(UUID studentId, String status) {}
}
