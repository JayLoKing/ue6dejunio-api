package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateAdaptationRequest(
    @NotNull @JsonProperty("id_curriculum_plan") UUID planId,
    @NotNull @JsonProperty("id_student") UUID studentId,
    // Every column is bounded here rather than at the database: `condition_type` is a varchar(120)
    // and the other three are text, so without a bound a body of any size reaches Postgres and
    // comes back as a 500 instead of the 400 that tells the teacher which field to shorten.
    @Size(max = 120) String conditionType,
    @Size(max = 4000) String adaptedContents,
    @Size(max = 4000) String adaptedMethodology,
    @Size(max = 4000) String adaptedCriteria
) {}
