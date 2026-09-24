package bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One message in someone's inbox.
 *
 * <p>Read is a stamp rather than a flag, and there is no boolean beside it: two columns holding one
 * fact drift the first time a write updates one and forgets the other.
 */
public record Notification(
        UUID id,
        UUID senderId,
        /** Absent when the system wrote it — a state change, or later the predictive model. */
        String senderName,
        UUID receiverId,
        String receiverName,
        NotificationType type,
        /** Only when the type is {@link NotificationType#CUSTOM}. */
        String subject,
        String message,
        /** What the notification is about, so the row is something the receiver can click. */
        String resourceType,
        UUID resourceId,
        /** When the receiver's inbox carried it back. */
        LocalDateTime deliveredAt,
        LocalDateTime readAt,
        LocalDateTime createdAt) {
    /** What the inbox shows as a badge. Derived, never stored. */
    public boolean read() {
        return readAt != null;
    }
}
