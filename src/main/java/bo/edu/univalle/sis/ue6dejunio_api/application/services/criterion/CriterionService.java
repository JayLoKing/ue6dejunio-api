package bo.edu.univalle.sis.ue6dejunio_api.application.services.criterion;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentDimension;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.CreateCriterionCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.UpdateCriterionCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CriterionService implements ICriterionService {

    private final ICriterionDomain criterionDomain;

    public CriterionService(ICriterionDomain criterionDomain) {
        this.criterionDomain = criterionDomain;
    }

    @Override
    @Transactional
    public EvaluationCriterion create(CreateCriterionCommand c) {
        if (!AssessmentDimension.isValid(c.dimension())) {
            throw new IllegalArgumentException("Dimension invalida: " + c.dimension());
        }
        if (!criterionDomain.classGroupExists(c.classGroupId())) {
            throw new ResourceNotFoundException("ClassGroup", c.classGroupId());
        }
        return criterionDomain.create(c.classGroupId(), c.trimester(), c.dimension(),
            c.name(), c.curriculumPlanId());
    }

    @Override
    @Transactional
    public EvaluationCriterion update(UUID id, UpdateCriterionCommand c) {
        getById(id);
        return criterionDomain.update(id, c.name());
    }

    @Override
    @Transactional(readOnly = true)
    public EvaluationCriterion getById(UUID id) {
        return criterionDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Criterion", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvaluationCriterion> list(UUID classGroupId, Integer trimester, String dimension) {
        if (dimension != null && !AssessmentDimension.isValid(dimension)) {
            throw new IllegalArgumentException("Dimension invalida: " + dimension);
        }
        return criterionDomain.list(classGroupId, trimester, dimension);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        criterionDomain.deleteById(id);
    }
}
