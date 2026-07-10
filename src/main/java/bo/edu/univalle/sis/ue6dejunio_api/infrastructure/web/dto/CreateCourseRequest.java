package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateCourseRequest(
    @NotNull @Positive @JsonProperty("id_grade") Integer gradeId,
    @NotNull @Positive @JsonProperty("id_parallel") Integer parallelId,
    @JsonProperty("id_homeroom_teacher") UUID homeroomTeacherId
) {}
