package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrollStudentRequest(
    @NotNull @JsonProperty("id_course") UUID courseId,
    @NotNull @Valid CreateStudentRequest student
) {}
