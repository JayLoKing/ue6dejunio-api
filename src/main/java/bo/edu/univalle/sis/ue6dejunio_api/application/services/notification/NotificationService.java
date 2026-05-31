package bo.edu.univalle.sis.ue6dejunio_api.application.services.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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
    public Page<Notification> inbox(UUID userId, boolean unreadOnly, Pageable pageable) {
        return notificationDomain.listReceived(userId, unreadOnly, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationDomain.unreadCount(userId);
    }

    @Override
    @Transactional
    public Notification markRead(UUID id, UUID userId) {
        Notification n = notificationDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notificacion", id));
        if (!userId.equals(n.receiverId())) {
            throw new AccessDeniedException("No es el receptor de esta notificacion");
        }
        notificationDomain.markAsRead(id);
        return notificationDomain.findById(id).orElseThrow();
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        return notificationDomain.markAllRead(userId);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID userId) {
        Notification n = notificationDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notificacion", id));
        if (!userId.equals(n.receiverId())) {
            throw new AccessDeniedException("No es el receptor de esta notificacion");
        }
        notificationDomain.deleteById(id);
    }
}
