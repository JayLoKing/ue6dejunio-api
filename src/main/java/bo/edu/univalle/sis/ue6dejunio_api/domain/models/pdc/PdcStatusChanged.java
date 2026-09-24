package bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.event.DomainEvent;
import java.util.UUID;

/**
 * A plan moved between the states its review walks through.
 *
 * <p>Carries what a reader needs rather than an id to go and fetch: whoever handles this runs after
 * the transaction committed, and re-reading the plan there would be a second query for facts that
 * were already in hand.
 *
 * @param authorId who wrote the plan — the one an approval or an observation is addressed to
 * @param observations what has to be corrected, present only when the status is WITH_OBSERVATIONS
 */
public record PdcStatusChanged(
        UUID planId,
        UUID authorId,
        /**
         * One of the {@link PdcStatus} constants — the plan keeps its status as text, not an enum.
         */
        String status,
        int planNumber,
        int trimester,
        String observations)
        implements DomainEvent {}
