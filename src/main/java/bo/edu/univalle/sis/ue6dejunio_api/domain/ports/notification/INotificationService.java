package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.SendNotificationCommand;

import java.util.UUID;

public interface INotificationService {
    Notification send(SendNotificationCommand command);
    PageResult<Notification> inbox(UUID userId, boolean unreadOnly, PageQuery pageQuery);
    long unreadCount(UUID userId);
    Notification markRead(UUID id);
    int markAllRead(UUID userId);
    void delete(UUID id);
}
