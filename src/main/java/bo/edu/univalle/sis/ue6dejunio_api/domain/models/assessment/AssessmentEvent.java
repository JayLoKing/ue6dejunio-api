package bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment;

import java.util.UUID;

/**
 * One scorable item of an activity ("Tema 1"). Its score shares the scale of the dimension its
 * criterion belongs to, so the item carries no ceiling of its own.
 */
public record AssessmentEvent(
        UUID id,
        UUID criterionId,
        UUID classGroupId,
        Integer trimester,
        String dimension,
        String title) {}
