package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param type a name outside the catalog is refused rather than stored, so an inbox never carries a
 *     heading no screen knows how to render
 * @param subject required only when the type is {@code CUSTOM}; the service enforces that, since
 *     the rule is about the pair rather than about either field
 */
public record SendNotificationRequest(
        @NotNull @JsonProperty("receiver_id") UUID receiverId,
        @NotNull NotificationType type,
        @Size(max = 150) String subject,
        @NotBlank @Size(max = 2000) String message,
        @Size(max = 40) @JsonProperty("resource_type") String resourceType,
        @JsonProperty("resource_id") UUID resourceId) {}
