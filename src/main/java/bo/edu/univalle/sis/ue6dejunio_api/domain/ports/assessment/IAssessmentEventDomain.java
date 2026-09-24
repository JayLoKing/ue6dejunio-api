package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IAssessmentEventDomain {
    AssessmentEvent create(UUID criterionId, String title);

    AssessmentEvent update(UUID id, String title);

    Optional<AssessmentEvent> findById(UUID id);

    List<AssessmentEvent> listByCriterion(UUID criterionId);

    /**
     * Whether the criterion already holds activity items. Asked instead of trusting {@code
     * activity_name} alone, because criteria created before that column existed carry items with no
     * activity name.
     */
    boolean hasItems(UUID criterionId);

    long countItems(UUID criterionId);

    /** Whether the item already carries scores, which deleting it would cascade away. */
    boolean hasScores(UUID eventId);

    void deleteById(UUID id);
}
