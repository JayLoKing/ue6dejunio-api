package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreatePdcRequest(
    @NotNull @JsonProperty("id_class_group") UUID classGroupId,
    @NotNull @Min(1) @Max(3) Integer trimester,
    @NotBlank @Size(max = 200) String title,
    String holisticObjective,
    String learningObjective,
    String contents,
    String practiceActivities,
    String theoryActivities,
    String valuationActivities,
    String productionActivities,
    String resources,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
    String criteriaBeing,
    String criteriaKnowing,
    String criteriaDoing,
    String criteriaDeciding
) {}
