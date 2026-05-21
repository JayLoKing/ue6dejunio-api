package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RegisterScoreRequest(
    @NotNull @JsonProperty("id_enrollment") UUID enrollmentId,
    @NotNull @Min(1) @Max(3) Integer trimester,
    @NotNull @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal scoreBeing,
    @NotNull @DecimalMin("0.0") @DecimalMax("45.0") BigDecimal scoreKnowing,
    @NotNull @DecimalMin("0.0") @DecimalMax("40.0") BigDecimal scoreDoing,
    @NotNull @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal scoreDeciding
) {}
