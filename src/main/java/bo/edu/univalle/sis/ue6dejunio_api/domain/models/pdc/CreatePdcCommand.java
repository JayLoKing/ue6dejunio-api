package bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc;

import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

@Builder
public record CreatePdcCommand(
    UUID classGroupId,
    Integer trimester,
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
    String criteriaDeciding
) {}
