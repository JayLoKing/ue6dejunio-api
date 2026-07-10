package bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment;

import java.math.BigDecimal;
import java.util.UUID;

public record AssessmentEvent(
    UUID id,
    UUID criterionId,
    UUID classGroupId,
    Integer trimester,
    String dimension,
    String title,
    String description,
    BigDecimal maxScore
) {}
