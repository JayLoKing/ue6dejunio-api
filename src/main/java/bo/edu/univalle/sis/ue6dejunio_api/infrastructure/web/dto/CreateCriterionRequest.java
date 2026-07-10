package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCriterionRequest(
    @NotNull @JsonProperty("id_class_group") UUID classGroupId,
    @NotNull @Min(1) @Max(3) Integer trimester,
    @NotBlank @Pattern(regexp = "Being|Knowing|Doing|Deciding") String dimension,
    @NotBlank @Size(max = 150) String name,
    @NotNull @DecimalMin("0.01") BigDecimal maxWeight,
    @JsonProperty("id_curriculum_plan") UUID curriculumPlanId
) {}
