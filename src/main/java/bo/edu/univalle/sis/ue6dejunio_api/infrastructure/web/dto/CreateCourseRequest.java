package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record CreateCourseRequest(
    @NotNull @Positive @JsonProperty("id_grade") Integer gradeId,
    @NotNull @Positive @JsonProperty("id_parallel") Integer parallelId,
    @JsonProperty("id_homeroom_teacher") UUID homeroomTeacherId,
    @NotEmpty @Valid List<Assignment> assignments
) {
    public record Assignment(
        @NotNull @JsonProperty("id_subject") UUID subjectId,
        @NotNull @JsonProperty("id_teacher") UUID teacherId
    ) {}
}
