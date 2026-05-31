package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

public record UpdateSubjectRequest(
    @Size(max = 100) String name,
    @Size(max = 100) String area,
    Boolean active
) {}
