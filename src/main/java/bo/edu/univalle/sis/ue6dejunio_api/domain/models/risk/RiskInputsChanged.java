package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.event.DomainEvent;
import java.util.UUID;

/**
 * Something the model is told about one subject changed: a mark, or how many criteria were planned.
 *
 * <p>Stated rather than acted on. The service that consolidated the mark does not know, and must
 * not know, that a prediction exists — it says what it did to the gradebook and stops. That the
 * risk model reads those same rows is the model's business.
 *
 * <p>It names no student even when one mark for one child caused it. The model's unit is the
 * subject for a trimester, because the features are read for a whole class group at once; a per
 * student event would be re-broadened at the first listener.
 */
public record RiskInputsChanged(UUID classGroupId, int trimester) implements DomainEvent {}
