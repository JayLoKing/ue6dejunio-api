package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record EnrollCourseRequest(
        @NotNull @JsonProperty("id_course") UUID courseId,
        @NotEmpty @Size(max = 500) @Valid List<CreateStudentRequest> students) {}
