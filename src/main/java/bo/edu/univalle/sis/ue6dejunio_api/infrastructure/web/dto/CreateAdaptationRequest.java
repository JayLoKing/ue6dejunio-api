package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAdaptationRequest(
    @NotNull @JsonProperty("id_curriculum_plan") UUID planId,
    @NotNull @JsonProperty("id_student") UUID studentId,
    String adaptedContents,
    String adaptedMethodology,
    String adaptedCriteria
) {}
