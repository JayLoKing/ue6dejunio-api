package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.event.DomainEvent;
import java.util.UUID;

/**
 * A withdrawn student was put back on a roll.
 *
 * <p>The mirror of {@link StudentWithdrawn}: it carries what the notice has to say, because whoever
 * handles it runs after the transaction committed. Which teachers hear about it is answered on the
 * notification side, from the course named here.
 *
 * @param studentName the full name, so the notice reads as a sentence rather than as a lookup
 * @param courseId the course whose seat was reopened or created
 * @param courseName the course as people call it, e.g. "Primero A"
 */
public record StudentReinstated(
        UUID studentId, String studentName, UUID courseId, String courseName)
        implements DomainEvent {}
