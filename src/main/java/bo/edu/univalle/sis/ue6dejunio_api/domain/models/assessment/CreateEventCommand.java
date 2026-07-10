package bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateEventCommand(
    UUID criterionId,
    String title,
    String description,
    BigDecimal maxScore
) {}
