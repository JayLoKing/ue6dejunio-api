package bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class Pdc {
    private UUID id;
    private UUID classGroupId;
    private String subjectName;
    private String teacherName;
    private java.util.UUID createdById;
    private java.util.UUID updatedById;
    private String updatedByName;
    private Integer trimester;
    private String status;
    private String reviewObservations;
    private String title;
    private String holisticObjective;
    private String learningObjective;
    private String contents;
    private String practiceActivities;
    private String theoryActivities;
    private String valuationActivities;
    private String productionActivities;
    private String resources;
    private LocalDate startDate;
    private LocalDate endDate;
    private String criteriaBeing;
    private String criteriaKnowing;
    private String criteriaDoing;
    private String criteriaDeciding;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
