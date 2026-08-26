package bo.edu.univalle.sis.ue6dejunio_api.application.services.criterion;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentDimension;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.CreateCriterionCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.UpdateCriterionCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CriterionService implements ICriterionService {

    private final ICriterionDomain criterionDomain;
    private final IAssessmentEventDomain eventDomain;

    public CriterionService(ICriterionDomain criterionDomain, IAssessmentEventDomain eventDomain) {
        this.criterionDomain = criterionDomain;
        this.eventDomain = eventDomain;
    }

    /**
     * Criterion and activity are one aggregate: an activity with no items, or items with no
     * criterion to roll into, is not a state the gradebook can score. They are therefore created
     * together or not at all.
     */
    @Override
    @Transactional
    public EvaluationCriterion create(CreateCriterionCommand c) {
        if (!AssessmentDimension.isValid(c.dimension())) {
            throw new ValidationException("Dimension invalida: " + c.dimension());
        }
        if (!criterionDomain.classGroupExists(c.classGroupId())) {
            throw new ResourceNotFoundException("ClassGroup", c.classGroupId());
        }
        // The adapter links the plan through getReferenceById, which never reads: an id that
        // matches nothing would only fail at flush, as a foreign key violation reported as 500.
        if (c.curriculumPlanId() != null && !criterionDomain.curriculumPlanExists(c.curriculumPlanId())) {
            throw new ResourceNotFoundException("CurriculumPlan", c.curriculumPlanId());
        }

        String activityName = trimToNull(c.activityName());
        List<String> items = normalizedItems(c.activityItems());

        if (activityName == null && !items.isEmpty()) {
            throw new ValidationException(
                "Los criterios de actividad requieren el nombre de la actividad");
        }
        if (activityName != null && items.isEmpty()) {
            throw new ValidationException(
                "Una actividad debe declarar al menos un criterio");
        }

        EvaluationCriterion created = criterionDomain.create(
            c.classGroupId(), c.trimester(), c.dimension(), c.name(), activityName,
            c.curriculumPlanId());

        for (String item : items) {
            eventDomain.create(created.id(), item);
        }
        return created;
    }

    @Override
    @Transactional
    public EvaluationCriterion update(UUID id, UpdateCriterionCommand c) {
        getById(id);
        if (criterionDomain.hasScoresForCriterion(id)) {
            throw new ConflictException(
                "El criterio no puede modificarse porque ya cuenta con calificaciones registradas");
        }
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
            throw new ValidationException("Dimension invalida: " + dimension);
        }
        return criterionDomain.list(classGroupId, trimester, dimension);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        if (criterionDomain.hasScoresForCriterion(id)) {
            throw new ConflictException(
                "El criterio no puede eliminarse porque ya cuenta con calificaciones registradas");
        }
        criterionDomain.deleteById(id);
    }

    /**
     * Blank entries are dropped and repeats rejected: two items sharing a name would give the
     * teacher two indistinguishable boxes, and the criterion's average would silently count one
     * of them twice.
     */
    private List<String> normalizedItems(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String candidate : raw) {
            String item = trimToNull(candidate);
            if (item == null) {
                continue;
            }
            if (!seen.add(item.toLowerCase(Locale.ROOT))) {
                throw new ValidationException("Criterio de actividad duplicado: " + item);
            }
            items.add(item);
        }
        return List.copyOf(items);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
