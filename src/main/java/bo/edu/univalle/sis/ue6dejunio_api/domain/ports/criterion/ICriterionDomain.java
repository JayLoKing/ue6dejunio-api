package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ICriterionDomain {
    boolean classGroupExists(UUID classGroupId);
    EvaluationCriterion create(UUID classGroupId, Integer trimester, String dimension,
                               String name, BigDecimal maxWeight, UUID curriculumPlanId);
    EvaluationCriterion update(UUID id, String name, BigDecimal maxWeight);
    Optional<EvaluationCriterion> findById(UUID id);
    List<EvaluationCriterion> list(UUID classGroupId, Integer trimester, String dimension);
    BigDecimal sumWeights(UUID classGroupId, Integer trimester, String dimension, UUID excludeId);
    void deleteById(UUID id);
}
