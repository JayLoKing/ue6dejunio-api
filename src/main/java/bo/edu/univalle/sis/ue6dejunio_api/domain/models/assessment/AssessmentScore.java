package bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A score the teacher typed. It targets exactly one of the two scorable units: an activity item
 * ({@code eventId}) or a criterion scored directly ({@code criterionId}). The database enforces the
 * same rule through {@code chk_score_target}.
 */
public record AssessmentScore(
        UUID id,
        UUID courseEnrollmentId,
        UUID eventId,
        UUID criterionId,
        BigDecimal score,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
