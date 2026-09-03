package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.sse;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationSent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns a written notification into a nudge down whatever streams its receiver has open.
 *
 * <p>{@code AFTER_COMMIT} for the same reason the PDC listener uses it: a send that is rolled back
 * must not tell a browser to go and read a row that does not exist. The browser would refetch,
 * find the old count, and the badge would simply not move — harmless, but it is a round trip spent
 * on a lie.
 */
@Component
public class NotificationStreamListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamListener.class);

    private final NotificationStreamRegistry registry;

    public NotificationStreamListener(NotificationStreamRegistry registry) {
        this.registry = registry;
    }

    /**
     * Never throws upward. The row is already committed and correct; a nudge that could not be
     * delivered costs the reader the 30-second poll they were going to make anyway.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationSent(NotificationSent event) {
        try {
            registry.push(event);
        } catch (RuntimeException ex) {
            log.error("The notification {} was written and could not be streamed to {}",
                event.notificationId(), event.receiverId(), ex);
        }
    }
}
