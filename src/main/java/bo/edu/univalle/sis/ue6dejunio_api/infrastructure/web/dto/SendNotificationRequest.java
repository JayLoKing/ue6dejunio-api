package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendNotificationRequest(
    @NotNull @JsonProperty("receiver_id") UUID receiverId,
    @NotBlank @Size(max = 2000) String message
) {}
