package bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation;

import java.util.UUID;

public record UpdateAdaptationCommand(
    String adaptedContents,
    String adaptedMethodology,
    String adaptedCriteria,
    UUID updatedBy
) {}
