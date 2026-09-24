package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.event.DomainEvent;
import java.util.UUID;

/**
 * A student was taken off the roll.
 *
 * <p>Carries what the notice has to say rather than an id to go and fetch it: whoever handles this
 * runs after the transaction committed, and the student's name and the reason were already in hand
 * where the decision was made.
 *
 * <p>Who should hear about it is not stated here. That is a question about the school — which
 * teachers ran the courses this student sat in — and it is answered on the notification side.
 *
 * @param studentName the full name, so the notice reads as a sentence rather than as a lookup
 * @param reason the category the withdrawal falls under
 * @param note the Director's own words, when the category did not say enough. May be {@code null}
 */
public record StudentWithdrawn(UUID studentId, String studentName, String reason, String note)
        implements DomainEvent {}
