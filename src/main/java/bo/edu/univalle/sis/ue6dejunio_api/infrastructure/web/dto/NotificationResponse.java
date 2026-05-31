package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    UUID senderId,
    String senderName,
    UUID receiverId,
    String receiverName,
    String message,
    boolean read,
    LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
            n.id(), n.senderId(), n.senderName(), n.receiverId(), n.receiverName(),
            n.message(), n.read(), n.createdAt());
    }
}
