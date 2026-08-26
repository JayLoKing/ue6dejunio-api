package bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion;

import java.util.UUID;

/**
 * A criterion carries one score per student in its dimension. That score is either recorded
 * directly on the criterion ({@code activityName == null}) or derived from the average of the
 * items of the activity that produced it ({@code activityName != null}). A criterion never
 * mixes both: see {@code chk_score_target} on {@code assessment_scores}.
 */
public record EvaluationCriterion(
    UUID id,
    UUID classGroupId,
    Integer trimester,
    String dimension,
    String name,
    String activityName,
    UUID curriculumPlanId
) {
    public boolean isActivityBased() {
        return activityName != null;
    }
}
