package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.CreateCriterionCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.UpdateCriterionCommand;

import java.util.List;
import java.util.UUID;

public interface ICriterionService {
    EvaluationCriterion create(CreateCriterionCommand command);
    EvaluationCriterion update(UUID id, UpdateCriterionCommand command);
    EvaluationCriterion getById(UUID id);
    List<EvaluationCriterion> list(UUID classGroupId, Integer trimester, String dimension);
    void delete(UUID id);
}
