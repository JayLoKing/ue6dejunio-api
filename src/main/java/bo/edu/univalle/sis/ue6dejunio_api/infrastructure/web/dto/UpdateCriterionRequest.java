package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateCriterionRequest(
    @Size(max = 150) String name,
    @DecimalMin("0.01") BigDecimal maxWeight
) {}
