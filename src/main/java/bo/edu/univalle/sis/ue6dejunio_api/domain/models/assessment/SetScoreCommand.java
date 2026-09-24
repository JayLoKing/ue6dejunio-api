package bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Exactly one of {@code eventId} / {@code criterionId} must be set; the service rejects the command
 * otherwise.
 */
public record SetScoreCommand(
        UUID courseEnrollmentId, UUID eventId, UUID criterionId, BigDecimal score, UUID createdBy) {
    public boolean targetsEvent() {
        return eventId != null;
    }
}
