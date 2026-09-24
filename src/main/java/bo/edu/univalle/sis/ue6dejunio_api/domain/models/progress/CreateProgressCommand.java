package bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateProgressCommand(
        UUID planId,
        LocalDate progressDate,
        String advancedContent,
        BigDecimal percentage,
        String observations,
        UUID createdBy) {}
