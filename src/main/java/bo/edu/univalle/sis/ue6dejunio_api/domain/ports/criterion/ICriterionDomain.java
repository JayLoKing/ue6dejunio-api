package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ICriterionDomain {
    boolean classGroupExists(UUID classGroupId);
    EvaluationCriterion create(UUID classGroupId, Integer trimester, String dimension,
                               String name, UUID curriculumPlanId);
    EvaluationCriterion update(UUID id, String name);
    Optional<EvaluationCriterion> findById(UUID id);
    List<EvaluationCriterion> list(UUID classGroupId, Integer trimester, String dimension);
    void deleteById(UUID id);
}
