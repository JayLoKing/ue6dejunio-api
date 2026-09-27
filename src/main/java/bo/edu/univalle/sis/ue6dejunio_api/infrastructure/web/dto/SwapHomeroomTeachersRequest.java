package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SwapHomeroomTeachersRequest(
        @NotNull @JsonProperty("id_course_a") UUID courseAId,
        @NotNull @JsonProperty("id_course_b") UUID courseBId) {}
