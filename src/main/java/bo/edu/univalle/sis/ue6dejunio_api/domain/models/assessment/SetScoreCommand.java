package bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment;

import java.math.BigDecimal;
import java.util.UUID;

public record SetScoreCommand(
    UUID courseEnrollmentId,
    UUID eventId,
    BigDecimal score,
    UUID createdBy
) {}
