package bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification;

import java.util.UUID;

/**
 * @param senderId the Director who wrote it, or null when the system did
 * @param subject  required only when the type is {@link NotificationType#CUSTOM}
 */
public record SendNotificationCommand(
    UUID senderId,
    UUID receiverId,
    NotificationType type,
    String subject,
    String message,
    String resourceType,
    UUID resourceId
) {}
