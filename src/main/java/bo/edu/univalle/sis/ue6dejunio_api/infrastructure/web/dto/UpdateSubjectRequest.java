package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * Edits a subject of the catalogue. Every field is optional, and an absent one is left as it is —
 * so the name has a lower bound: {@code ""} would blank a column the schema declares NOT NULL and
 * that nothing in the system can be looked up by afterwards.
 */
public record UpdateSubjectRequest(
    @Size(min = 1, max = 100) String name,
    @JsonProperty("id_area") Integer areaId,
    Boolean technical,
    Boolean active
) {}
