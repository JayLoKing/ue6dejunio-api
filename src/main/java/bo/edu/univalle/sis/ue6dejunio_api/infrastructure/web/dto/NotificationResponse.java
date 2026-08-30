package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    UUID senderId,
    String senderName,
    UUID receiverId,
    String receiverName,
    NotificationType type,
    String subject,
    String message,
    String resourceType,
    UUID resourceId,
    LocalDateTime deliveredAt,
    LocalDateTime readAt,
    /** Derived from readAt. Kept because the inbox badge asks a yes-or-no question. */
    boolean read,
    LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
            n.id(), n.senderId(), n.senderName(), n.receiverId(), n.receiverName(),
            n.type(), n.subject(), n.message(), n.resourceType(), n.resourceId(),
            n.deliveredAt(), n.readAt(), n.read(), n.createdAt());
    }
}
