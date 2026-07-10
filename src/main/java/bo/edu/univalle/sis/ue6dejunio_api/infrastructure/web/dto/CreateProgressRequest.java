package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateProgressRequest(
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate progressDate,
    String advancedContent,
    @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal percentage,
    String observations
) {}
