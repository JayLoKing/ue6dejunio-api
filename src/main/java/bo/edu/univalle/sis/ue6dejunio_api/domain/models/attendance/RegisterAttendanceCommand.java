package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import java.time.LocalDate;
import java.util.UUID;

public record RegisterAttendanceCommand(
    UUID enrollmentId,
    LocalDate date,
    String status
) {}
