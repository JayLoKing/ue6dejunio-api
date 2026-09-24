package bo.edu.univalle.sis.ue6dejunio_api.application.services.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.CreateEventCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.UpdateEventCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionDomain;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentEventService implements IAssessmentEventService {

    private final IAssessmentEventDomain eventDomain;
    private final ICriterionDomain criterionDomain;

    public AssessmentEventService(
            IAssessmentEventDomain eventDomain, ICriterionDomain criterionDomain) {
        this.eventDomain = eventDomain;
        this.criterionDomain = criterionDomain;
    }

    /**
     * Only a criterion that declares an activity may gain items. Adding one to a directly scored
     * criterion would leave it with two competing sources of truth for the same score.
     */
    @Override
    @Transactional
    public AssessmentEvent create(CreateEventCommand c) {
        EvaluationCriterion criterion =
                criterionDomain
                        .findById(c.criterionId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Criterion", c.criterionId()));
        if (!criterion.isActivityBased()) {
            throw new ConflictException(
                    "El criterio no pertenece a una actividad: se califica de forma directa");
        }
        return eventDomain.create(c.criterionId(), c.title());
    }

    /**
     * Two states an item may not be deleted from.
     *
     * <p>Carrying scores: the database cascades them away, and nothing would recompute {@code
     * academic_scores}, so the trimester would keep an average built on rows that no longer exist.
     * This mirrors how {@code CriterionService} guards its own delete.
     *
     * <p>Being the activity's last item: the criterion would have nothing left to average and still
     * refuse a direct score, surviving as a criterion nobody can score.
     */
    @Override
    @Transactional
    public void delete(UUID id) {
        AssessmentEvent event = getById(id);
        if (eventDomain.hasScores(id)) {
            throw new ConflictException(
                    "El criterio de actividad no puede eliminarse porque ya cuenta con"
                            + " calificaciones registradas");
        }
        EvaluationCriterion criterion =
                criterionDomain
                        .findById(event.criterionId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Criterion", event.criterionId()));
        if (criterion.isActivityBased() && eventDomain.countItems(event.criterionId()) == 1) {
            throw new ConflictException(
                    "No puede eliminarse el ultimo criterio de la actividad \""
                            + criterion.activityName()
                            + "\": elimine el criterio agrupador completo");
        }
        eventDomain.deleteById(id);
    }

    /** A null title means "leave it"; a blank one would silently wipe a valid name. */
    @Override
    @Transactional
    public AssessmentEvent update(UUID id, UpdateEventCommand c) {
        getById(id);
        if (c.title() != null && c.title().isBlank()) {
            throw new ValidationException(
                    "El nombre del criterio de actividad no puede estar vacio");
        }
        return eventDomain.update(id, c.title());
    }

    @Override
    @Transactional(readOnly = true)
    public AssessmentEvent getById(UUID id) {
        return eventDomain
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AssessmentEvent", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentEvent> listByCriterion(UUID criterionId) {
        return eventDomain.listByCriterion(criterionId);
    }
}
