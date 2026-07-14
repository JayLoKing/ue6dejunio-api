package bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion;

import java.util.UUID;

public record CreateCriterionCommand(
    UUID classGroupId,
    Integer trimester,
    String dimension,
    String name,
    UUID curriculumPlanId
) {}
