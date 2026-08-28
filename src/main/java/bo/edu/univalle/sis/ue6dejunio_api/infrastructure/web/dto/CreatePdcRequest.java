package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Opens a month's plan. Only the heading is asked for here — the subject blocks are written one at
 * a time afterwards, which is how the form walks the teacher through them.
 *
 * @param classGroupIds omit to plan every subject the caller teaches in the course — every one of
 *                      them for a homeroom teacher. Capped because no course carries thirty active
 *                      subjects, and an authenticated write should not be able to post a list
 *                      without end.
 */
public record CreatePdcRequest(
    @NotNull @JsonProperty("id_course") UUID courseId,
    @NotNull @Min(1) @Max(12) @JsonProperty("plan_number") Integer planNumber,
    @NotNull @Min(1) @Max(3) Integer trimester,
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") @JsonProperty("period_start") LocalDate periodStart,
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") @JsonProperty("period_end") LocalDate periodEnd,
    @Size(max = 4000) String holisticObjective,
    @Size(max = 4000) String finalProduct,
    @Size(max = 4000) String bibliography,
    @Size(max = 30) @JsonProperty("id_class_groups") List<UUID> classGroupIds
) {}
