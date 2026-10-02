package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DuplicateYearStructureRequest(
        @NotNull @Positive @JsonProperty("source_academic_year_id") Integer sourceAcademicYearId) {}
