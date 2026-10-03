package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.ScoreAuthor;
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
        LocalDateTime updatedAt,
        Author createdBy,
        Author updatedBy) {

    /** Who wrote a score; null on the response when the row predates the audit columns. */
    public record Author(UUID id, String fullName) {
        static Author of(ScoreAuthor a) {
            return a == null ? null : new Author(a.id(), a.fullName());
        }
    }

    public static AssessmentScoreResponse from(AssessmentScore s) {
        return new AssessmentScoreResponse(
                s.id(),
                s.courseEnrollmentId(),
                s.eventId(),
                s.criterionId(),
                s.score(),
                s.createdAt(),
                s.updatedAt(),
                Author.of(s.createdBy()),
                Author.of(s.updatedBy()));
    }
}
