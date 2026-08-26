package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;

import java.util.Optional;
import java.util.UUID;

public interface INotificationDomain {
    boolean userExists(UUID userId);
    Notification send(UUID senderId, UUID receiverId, String message);
    Optional<Notification> findById(UUID id);
    PageResult<Notification> listReceived(UUID receiverId, boolean unreadOnly, PageQuery pageQuery);
    long unreadCount(UUID receiverId);
    void markAsRead(UUID id);
    int markAllRead(UUID receiverId);
    void deleteById(UUID id);
}
