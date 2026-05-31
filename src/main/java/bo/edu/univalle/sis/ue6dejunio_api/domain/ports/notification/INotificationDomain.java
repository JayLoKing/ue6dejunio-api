package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface INotificationDomain {
    boolean userExists(UUID userId);
    Notification send(UUID senderId, UUID receiverId, String message);
    Optional<Notification> findById(UUID id);
    Page<Notification> listReceived(UUID receiverId, boolean unreadOnly, Pageable pageable);
    long unreadCount(UUID receiverId);
    void markAsRead(UUID id);
    int markAllRead(UUID receiverId);
    void deleteById(UUID id);
}
