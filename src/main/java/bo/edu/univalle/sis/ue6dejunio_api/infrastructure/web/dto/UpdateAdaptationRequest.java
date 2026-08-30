package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

/** An omitted column keeps the value the row already holds, so a step saves only what it asks for. */
public record UpdateAdaptationRequest(
    @Size(max = 120) String conditionType,
    @Size(max = 4000) String adaptedContents,
    @Size(max = 4000) String adaptedMethodology,
    @Size(max = 4000) String adaptedCriteria
) {}
