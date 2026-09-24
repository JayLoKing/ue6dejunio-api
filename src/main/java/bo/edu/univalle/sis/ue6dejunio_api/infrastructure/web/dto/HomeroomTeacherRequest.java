package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record HomeroomTeacherRequest(
        @NotNull @JsonProperty("id_homeroom_teacher") UUID homeroomTeacherId) {}
