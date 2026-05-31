package bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification;

import java.time.LocalDateTime;
import java.util.UUID;

public record Notification(
    UUID id,
    UUID senderId,
    String senderName,
    UUID receiverId,
    String receiverName,
    String message,
    boolean read,
    LocalDateTime createdAt
) {}
