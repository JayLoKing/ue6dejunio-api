package bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification;

import java.util.UUID;

public record SendNotificationCommand(UUID senderId, UUID receiverId, String message) {}
