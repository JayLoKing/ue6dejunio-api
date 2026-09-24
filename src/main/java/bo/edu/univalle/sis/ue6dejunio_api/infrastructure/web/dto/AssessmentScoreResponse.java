package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AssessmentScoreResponse(
        UUID id,
        UUID courseEnrollmentId,
        UUID eventId,
        UUID criterionId,
        BigDecimal score,
        LocalDateTime recordedAt,
        LocalDateTime updatedAt) {
    public static AssessmentScoreResponse from(AssessmentScore s) {
        return new AssessmentScoreResponse(
                s.id(),
                s.courseEnrollmentId(),
                s.eventId(),
                s.criterionId(),
                s.score(),
                s.createdAt(),
                s.updatedAt());
    }
}
