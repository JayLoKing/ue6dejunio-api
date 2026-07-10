package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;

import java.math.BigDecimal;
import java.util.UUID;

public record AssessmentScoreResponse(
    UUID id,
    UUID courseEnrollmentId,
    UUID eventId,
    BigDecimal score
) {
    public static AssessmentScoreResponse from(AssessmentScore s) {
        return new AssessmentScoreResponse(s.id(), s.courseEnrollmentId(), s.eventId(), s.score());
    }
}
