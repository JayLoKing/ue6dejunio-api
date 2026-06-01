package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PdcResponse(
    UUID id,
    UUID classGroupId,
    String subjectName,
    String teacherName,
    UUID createdById,
    UUID updatedById,
    String updatedByName,
    Integer trimester,
    String status,
    String reviewObservations,
    String title,
    String holisticObjective,
    String learningObjective,
    String contents,
    String practiceActivities,
    String theoryActivities,
    String valuationActivities,
    String productionActivities,
    String resources,
    LocalDate startDate,
    LocalDate endDate,
    String criteriaBeing,
    String criteriaKnowing,
    String criteriaDoing,
    String criteriaDeciding,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static PdcResponse from(Pdc p) {
        return new PdcResponse(
            p.getId(), p.getClassGroupId(), p.getSubjectName(), p.getTeacherName(),
            p.getCreatedById(), p.getUpdatedById(), p.getUpdatedByName(),
            p.getTrimester(), p.getStatus(), p.getReviewObservations(), p.getTitle(),
            p.getHolisticObjective(), p.getLearningObjective(), p.getContents(),
            p.getPracticeActivities(), p.getTheoryActivities(), p.getValuationActivities(),
            p.getProductionActivities(), p.getResources(), p.getStartDate(), p.getEndDate(),
            p.getCriteriaBeing(), p.getCriteriaKnowing(), p.getCriteriaDoing(), p.getCriteriaDeciding(),
            p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}
