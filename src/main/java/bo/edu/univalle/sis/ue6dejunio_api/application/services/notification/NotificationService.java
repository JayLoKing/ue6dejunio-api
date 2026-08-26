package bo.edu.univalle.sis.ue6dejunio_api.application.services.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationService implements INotificationService {

    private final INotificationDomain notificationDomain;

    public NotificationService(INotificationDomain notificationDomain) {
        this.notificationDomain = notificationDomain;
    }

    @Override
    @Transactional
    public Notification send(SendNotificationCommand c) {
        if (!notificationDomain.userExists(c.receiverId())) {
            throw new ResourceNotFoundException("Usuario receptor", c.receiverId());
        }
        return notificationDomain.send(c.senderId(), c.receiverId(), c.message());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Notification> inbox(UUID userId, boolean unreadOnly, PageQuery pageQuery) {
        return notificationDomain.listReceived(userId, unreadOnly, pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationDomain.unreadCount(userId);
    }

    @Override
    @Transactional
    public Notification markRead(UUID id) {
        // Only the receiver reaches this: @PreAuthorize resolves that before the call.
        Notification n = notificationDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notificacion", id));
        notificationDomain.markAsRead(id);
        // Read once and report what the row now is. Fetching it again cost a round trip and, if
        // the receiver deleted it from another tab meanwhile, answered 500 instead of 404.
        return new Notification(n.id(), n.senderId(), n.senderName(), n.receiverId(),
            n.receiverName(), n.message(), true, n.createdAt());
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        return notificationDomain.markAllRead(userId);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        notificationDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notificacion", id));
        notificationDomain.deleteById(id);
    }
}
