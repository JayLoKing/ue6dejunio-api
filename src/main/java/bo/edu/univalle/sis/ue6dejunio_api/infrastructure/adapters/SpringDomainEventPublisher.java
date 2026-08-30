package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.event.DomainEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The port, over Spring's own publisher.
 *
 * <p>Thin on purpose. What the framework contributes is not the dispatch — that part is a loop —
 * but the ability for a listener to run after the transaction commits, which is what keeps a
 * rolled-back state change from announcing itself.
 */
@Component
public class SpringDomainEventPublisher implements IDomainEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
