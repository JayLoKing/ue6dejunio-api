package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateClassGroupRequest(
    @NotNull @JsonProperty("id_grade") Integer gradeId,
    @NotNull @JsonProperty("id_parallel") Integer parallelId,
    @NotEmpty @Valid List<Assignment> assignments
) {
    public record Assignment(
        @NotNull @JsonProperty("id_subject") UUID subjectId,
        @NotNull @JsonProperty("id_teacher") UUID teacherId
    ) {}
}
