package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface INotificationService {
    Notification send(SendNotificationCommand command);
    Page<Notification> inbox(UUID userId, boolean unreadOnly, Pageable pageable);
    long unreadCount(UUID userId);
    Notification markRead(UUID id, UUID userId);
    int markAllRead(UUID userId);
    void delete(UUID id, UUID userId);
}
