package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateEventRequest(
    @NotNull @JsonProperty("id_criterion") UUID criterionId,
    @NotBlank @Size(max = 150) String title
) {}
