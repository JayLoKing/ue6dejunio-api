package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

public record UpdateAdaptationRequest(
    String adaptedContents,
    String adaptedMethodology,
    String adaptedCriteria
) {}
