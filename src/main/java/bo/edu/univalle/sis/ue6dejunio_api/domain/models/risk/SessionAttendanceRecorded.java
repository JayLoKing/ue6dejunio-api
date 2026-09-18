package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.event.DomainEvent;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A roll call taken inside one subject.
 *
 * <p>Carries the date and not a trimester because that is what the write knows: attendance rows are
 * dated, and which trimester a date belongs to is a row in {@code academic_trimesters} that the
 * course's own gestión decides. Resolving it here would be this side guessing at a calendar it does
 * not hold.
 */
public record SessionAttendanceRecorded(UUID classGroupId, LocalDate date) implements DomainEvent {
}
