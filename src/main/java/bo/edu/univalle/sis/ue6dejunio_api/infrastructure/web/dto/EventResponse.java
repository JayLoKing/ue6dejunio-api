package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;

import java.math.BigDecimal;
import java.util.UUID;

public record EventResponse(
    UUID id,
    UUID criterionId,
    UUID classGroupId,
    Integer trimester,
    String dimension,
    String title,
    String description,
    BigDecimal maxScore
) {
    public static EventResponse from(AssessmentEvent e) {
        return new EventResponse(e.id(), e.criterionId(), e.classGroupId(), e.trimester(),
            e.dimension(), e.title(), e.description(), e.maxScore());
    }
}
