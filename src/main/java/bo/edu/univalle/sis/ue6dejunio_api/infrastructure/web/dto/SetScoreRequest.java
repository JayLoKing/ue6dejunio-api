package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SetScoreRequest(
    @NotNull @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
    @NotNull @JsonProperty("id_assessment_event") UUID eventId,
    @NotNull @DecimalMin("0.0") BigDecimal score
) {}
