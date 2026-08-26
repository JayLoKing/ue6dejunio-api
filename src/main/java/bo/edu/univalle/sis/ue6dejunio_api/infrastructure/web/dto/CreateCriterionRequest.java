package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Creates a criterion, optionally together with the activity that feeds it. Omit {@code activity}
 * and the criterion is scored directly; include it and the criterion's score becomes the average
 * of the activity's items.
 */
public record CreateCriterionRequest(
    @NotNull @JsonProperty("id_class_group") UUID classGroupId,
    @NotNull @Min(1) @Max(3) Integer trimester,
    @NotBlank @Pattern(regexp = "Being|Knowing|Doing|Deciding") String dimension,
    @NotBlank @Size(max = 150) String name,
    @Valid Activity activity,
    @JsonProperty("id_curriculum_plan") UUID curriculumPlanId
) {
    /**
     * The bound on {@code items} is structural, not cosmetic: every entry becomes one insert
     * inside a single transaction, so an unbounded list is an open door to a write of any size.
     * Fifty is far above any real activity — a trimester's notebook review runs to a handful.
     */
    public record Activity(
        @NotBlank @Size(max = 150) String title,
        @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 150) String> items
    ) {}

    public String activityTitle() {
        return activity != null ? activity.title() : null;
    }

    public List<String> activityItems() {
        return activity != null ? activity.items() : List.of();
    }
}
