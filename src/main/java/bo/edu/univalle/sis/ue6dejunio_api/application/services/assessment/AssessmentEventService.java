package bo.edu.univalle.sis.ue6dejunio_api.application.services.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.CreateEventCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.UpdateEventCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AssessmentEventService implements IAssessmentEventService {

    private static final BigDecimal DEFAULT_MAX = new BigDecimal("100");

    private final IAssessmentEventDomain eventDomain;

    public AssessmentEventService(IAssessmentEventDomain eventDomain) {
        this.eventDomain = eventDomain;
    }

    @Override
    @Transactional
    public AssessmentEvent create(CreateEventCommand c) {
        if (!eventDomain.criterionExists(c.criterionId())) {
            throw new ResourceNotFoundException("Criterion", c.criterionId());
        }
        BigDecimal max = c.maxScore() != null ? c.maxScore() : DEFAULT_MAX;
        if (max.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("maxScore debe ser mayor a 0");
        }
        return eventDomain.create(c.criterionId(), c.title(), c.description(), max);
    }

    @Override
    @Transactional
    public AssessmentEvent update(UUID id, UpdateEventCommand c) {
        getById(id);
        return eventDomain.update(id, c.title(), c.description(), c.maxScore());
    }

    @Override
    @Transactional(readOnly = true)
    public AssessmentEvent getById(UUID id) {
        return eventDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AssessmentEvent", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentEvent> listByCriterion(UUID criterionId) {
        return eventDomain.listByCriterion(criterionId);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        eventDomain.deleteById(id);
    }
}
