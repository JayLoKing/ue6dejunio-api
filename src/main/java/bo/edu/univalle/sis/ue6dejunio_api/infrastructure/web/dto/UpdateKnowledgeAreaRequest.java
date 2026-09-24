package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param displayOrder optional: absent, the area stays where it was
 */
public record UpdateKnowledgeAreaRequest(
        @NotBlank @Size(max = 80) String name, @Min(1) Integer displayOrder) {}
