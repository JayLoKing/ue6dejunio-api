package bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion;

import java.math.BigDecimal;
import java.util.UUID;

public record EvaluationCriterion(
    UUID id,
    UUID classGroupId,
    Integer trimester,
    String dimension,
    String name,
    BigDecimal maxWeight,
    UUID curriculumPlanId
) {}
