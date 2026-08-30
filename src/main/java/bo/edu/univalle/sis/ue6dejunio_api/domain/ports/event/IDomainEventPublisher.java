package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.event.DomainEvent;

/**
 * Where a service says what happened without knowing who cares.
 *
 * <p>A port rather than the framework's publisher injected directly, for the same reason every
 * other outward call in this application is one: the service that approves a plan should be
 * readable, and testable, without dragging in how events are dispatched.
 */
public interface IDomainEventPublisher {

    /**
     * States the fact. Whoever is listening decides what it means, and does so only once the
     * transaction that produced it has committed.
     */
    void publish(DomainEvent event);
}
