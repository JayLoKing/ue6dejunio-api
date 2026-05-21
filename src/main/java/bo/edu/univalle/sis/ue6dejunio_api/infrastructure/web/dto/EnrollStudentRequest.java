package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record EnrollStudentRequest(
    @NotNull @JsonProperty("id_grade") Integer gradeId,
    @NotNull @JsonProperty("id_parallel") Integer parallelId,
    @NotNull @Valid CreateStudentRequest student
) {}
