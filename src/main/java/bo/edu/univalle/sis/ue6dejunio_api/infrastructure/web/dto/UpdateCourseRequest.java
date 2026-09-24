package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record UpdateCourseRequest(
        @JsonProperty("id_homeroom_teacher") UUID homeroomTeacherId, Boolean active) {}
