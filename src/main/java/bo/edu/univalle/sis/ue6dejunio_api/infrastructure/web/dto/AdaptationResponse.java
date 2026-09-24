package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdaptationResponse(
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
        LocalDateTime updatedAt) {
    public static AdaptationResponse from(Adaptation a) {
        return new AdaptationResponse(
                a.id(),
                a.planId(),
                a.studentId(),
                a.studentName(),
                a.conditionType(),
                a.adaptedContents(),
                a.adaptedMethodology(),
                a.adaptedCriteria(),
                a.createdById(),
                a.updatedById(),
                a.createdAt(),
                a.updatedAt());
    }
}
