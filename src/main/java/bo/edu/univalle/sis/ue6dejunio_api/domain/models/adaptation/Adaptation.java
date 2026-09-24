package bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation;

import java.time.LocalDateTime;
import java.util.UUID;

public record Adaptation(
        UUID id,
        UUID planId,
        UUID studentId,
        String studentName,
        String conditionType,
        String adaptedContents,
        String adaptedMethodology,
        String adaptedCriteria,
        UUID createdById,
        UUID updatedById,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
