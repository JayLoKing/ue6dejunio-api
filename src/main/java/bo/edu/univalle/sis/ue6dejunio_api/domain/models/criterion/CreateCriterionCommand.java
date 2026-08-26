package bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion;

import java.util.List;
import java.util.UUID;

/**
 * Creates a criterion and, optionally, the activity that feeds it in the same transaction.
 * When {@code activityName} is present, {@code activityItems} holds the names of the activity's
 * own criteria ("Tema 1", "Tema 2"); each becomes a scorable item and the criterion's score is
 * their average.
 */
public record CreateCriterionCommand(
    UUID classGroupId,
    Integer trimester,
    String dimension,
    String name,
    String activityName,
    List<String> activityItems,
    UUID curriculumPlanId
) {}
