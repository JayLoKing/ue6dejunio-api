package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ICriterionDomain {
    boolean classGroupExists(UUID classGroupId);

    boolean curriculumPlanExists(UUID curriculumPlanId);

    EvaluationCriterion create(UUID classGroupId, Integer trimester, String dimension,
                               String name, String activityName, UUID curriculumPlanId);

    EvaluationCriterion update(UUID id, String name);

    Optional<EvaluationCriterion> findById(UUID id);

    List<EvaluationCriterion> list(UUID classGroupId, Integer trimester, String dimension);

    void deleteById(UUID id);

    /** True when the criterion carries scores on either target: its own, or its activity items. */
    boolean hasScoresForCriterion(UUID id);
}
