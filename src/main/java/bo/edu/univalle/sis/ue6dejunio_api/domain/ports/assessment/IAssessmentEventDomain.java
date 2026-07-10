package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IAssessmentEventDomain {
    boolean criterionExists(UUID criterionId);
    AssessmentEvent create(UUID criterionId, String title, String description, BigDecimal maxScore);
    AssessmentEvent update(UUID id, String title, String description, BigDecimal maxScore);
    Optional<AssessmentEvent> findById(UUID id);
    List<AssessmentEvent> listByCriterion(UUID criterionId);
    void deleteById(UUID id);
}
