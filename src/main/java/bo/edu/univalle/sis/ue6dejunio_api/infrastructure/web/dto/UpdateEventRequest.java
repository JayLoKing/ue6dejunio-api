package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

public record UpdateEventRequest(
    @Size(max = 150) String title
) {}
