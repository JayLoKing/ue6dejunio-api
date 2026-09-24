package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.PlanProgress;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProgressResponse(
        UUID id,
        UUID planId,
        LocalDate progressDate,
        String advancedContent,
        BigDecimal percentage,
        String observations,
        UUID createdBy,
        LocalDateTime createdAt) {
    public static ProgressResponse from(PlanProgress p) {
        return new ProgressResponse(
                p.id(),
                p.planId(),
                p.progressDate(),
                p.advancedContent(),
                p.percentage(),
                p.observations(),
                p.createdBy(),
                p.createdAt());
    }
}
