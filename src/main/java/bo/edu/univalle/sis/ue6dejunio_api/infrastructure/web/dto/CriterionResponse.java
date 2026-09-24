package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import java.util.UUID;

public record CriterionResponse(
        UUID id,
        UUID classGroupId,
        Integer trimester,
        String dimension,
        String name,
        String activityName,
        UUID curriculumPlanId) {
    public static CriterionResponse from(EvaluationCriterion c) {
        return new CriterionResponse(
                c.id(),
                c.classGroupId(),
                c.trimester(),
                c.dimension(),
                c.name(),
                c.activityName(),
                c.curriculumPlanId());
    }
}
