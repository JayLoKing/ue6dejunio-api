package bo.edu.univalle.sis.ue6dejunio_api.application.services.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * One notification, one transaction of its own.
 *
 * <p>Exists so a caller can survive a failed send. A listener that wrapped its own transaction
 * around several sends and caught the failures did not survive them: the failed send marks the
 * transaction rollback-only, the catch hides that from the caller, and the boundary then throws on
 * commit anyway — after taking the rows that had already succeeded down with it. Telling three
 * people has to be three outcomes, not one.
 *
 * <p>A separate bean rather than a private method because a class calling itself does not pass
 * through the proxy, and the annotation would do nothing at all.
 */
@Component
public class NotificationDispatcher {

    private final INotificationService notificationService;

    public NotificationDispatcher(INotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * {@code REQUIRES_NEW} rather than the default: this runs after another transaction committed,
     * where relying on there being one to join is how a write ends up silently discarded.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(SendNotificationCommand command) {
        notificationService.send(command);
    }
}
