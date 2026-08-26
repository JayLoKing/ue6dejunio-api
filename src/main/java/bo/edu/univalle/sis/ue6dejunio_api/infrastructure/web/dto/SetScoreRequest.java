package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Send {@code id_assessment_event} to score an activity item, or {@code id_criterion} to score a
 * criterion directly. Exactly one of them; the service rejects a body carrying both or neither.
 *
 * @param score a coarse ceiling only. The real cap is per dimension (Being 10, Knowing 45,
 *     Doing 40, Deciding 5) and cannot be known until the target is resolved, so the service
 *     still owns the exact check; this keeps an absurd payload from reaching it.
 */
public record SetScoreRequest(
    @NotNull @JsonProperty("id_course_enrollment") UUID courseEnrollmentId,
    @JsonProperty("id_assessment_event") UUID eventId,
    @JsonProperty("id_criterion") UUID criterionId,
    @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal score
) {}
