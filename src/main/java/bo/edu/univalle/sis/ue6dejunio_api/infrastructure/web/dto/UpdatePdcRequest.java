package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdatePdcRequest(
    @Size(max = 200) String title,
    String holisticObjective,
    String learningObjective,
    String contents,
    String practiceActivities,
    String theoryActivities,
    String valuationActivities,
    String productionActivities,
    String resources,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
    String criteriaBeing,
    String criteriaKnowing,
    String criteriaDoing,
    String criteriaDeciding
) {}
