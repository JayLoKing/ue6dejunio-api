package bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AssessmentScore(
    UUID id,
    UUID courseEnrollmentId,
    UUID eventId,
    BigDecimal score,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
