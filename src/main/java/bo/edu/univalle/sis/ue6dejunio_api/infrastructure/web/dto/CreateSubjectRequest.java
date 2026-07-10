package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(
    @NotBlank @Size(max = 100) String name,
    Boolean technical
) {}
