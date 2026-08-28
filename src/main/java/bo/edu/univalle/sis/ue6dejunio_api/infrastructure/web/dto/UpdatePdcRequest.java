package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Edits the heading of a plan. Every field is optional so the stepped form can save the step the
 * teacher is on without sending back the ones they have not reached.
 *
 * <p>The three text fields take an empty string on purpose: absent means "leave it as it is", and
 * {@code ""} is the only way the form has of emptying a box the teacher cleared. They are free
 * prose no lookup depends on, unlike a name.
 */
public record UpdatePdcRequest(
    @Min(1) @Max(12) @JsonProperty("plan_number") Integer planNumber,
    @JsonFormat(pattern = "yyyy-MM-dd") @JsonProperty("period_start") LocalDate periodStart,
    @JsonFormat(pattern = "yyyy-MM-dd") @JsonProperty("period_end") LocalDate periodEnd,
    @Size(max = 4000) String holisticObjective,
    @Size(max = 4000) String finalProduct,
    @Size(max = 4000) String bibliography
) {}
