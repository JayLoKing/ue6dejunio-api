package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AssessmentEventEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.EvaluationCriterionEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentEventRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaEvaluationCriterionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class AssessmentEventRepositoryAdapter implements IAssessmentEventDomain {

    private final JpaAssessmentEventRepository eventRepo;
    private final JpaEvaluationCriterionRepository criterionRepo;

    public AssessmentEventRepositoryAdapter(JpaAssessmentEventRepository eventRepo,
                                            JpaEvaluationCriterionRepository criterionRepo) {
        this.eventRepo = eventRepo;
        this.criterionRepo = criterionRepo;
    }

    @Override
    public boolean criterionExists(UUID criterionId) {
        return criterionRepo.existsById(criterionId);
    }

    @Override
    @Transactional
    public AssessmentEvent create(UUID criterionId, String title, String description, BigDecimal maxScore) {
        AssessmentEventEntity e = new AssessmentEventEntity();
        e.setCriterion(criterionRepo.getReferenceById(criterionId));
        e.setTitle(title);
        e.setDescription(description);
        e.setMaxScore(maxScore);
        e.setCreatedAt(LocalDateTime.now());
        return toDomain(eventRepo.save(e));
    }

    @Override
    @Transactional
    public AssessmentEvent update(UUID id, String title, String description, BigDecimal maxScore) {
        AssessmentEventEntity e = eventRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AssessmentEvent", id));
        if (title != null) e.setTitle(title);
        if (description != null) e.setDescription(description);
        if (maxScore != null) e.setMaxScore(maxScore);
        return toDomain(eventRepo.save(e));
    }

    @Override
    public Optional<AssessmentEvent> findById(UUID id) {
        return eventRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<AssessmentEvent> listByCriterion(UUID criterionId) {
        return eventRepo.findByCriterion_IdOrderByCreatedAt(criterionId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        eventRepo.deleteById(id);
    }

    private AssessmentEvent toDomain(AssessmentEventEntity e) {
        EvaluationCriterionEntity c = e.getCriterion();
        return new AssessmentEvent(
            e.getId(), c.getId(),
            c.getClassGroup() != null ? c.getClassGroup().getId() : null,
            c.getTrimester(), c.getDimension(),
            e.getTitle(), e.getDescription(), e.getMaxScore());
    }
}
