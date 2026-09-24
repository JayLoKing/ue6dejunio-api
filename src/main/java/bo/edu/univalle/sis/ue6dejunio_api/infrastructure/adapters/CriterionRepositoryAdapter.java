package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.EvaluationCriterionEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers.CriterionMapper;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentScoreRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCurriculumPlanRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaEvaluationCriterionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CriterionRepositoryAdapter implements ICriterionDomain {

    private final JpaEvaluationCriterionRepository criterionRepo;
    private final JpaClassGroupRepository classGroupRepo;
    private final JpaCurriculumPlanRepository planRepo;
    private final JpaAssessmentScoreRepository assessmentScoreRepo;
    private final CriterionMapper mapper;

    public CriterionRepositoryAdapter(
            JpaEvaluationCriterionRepository criterionRepo,
            JpaClassGroupRepository classGroupRepo,
            JpaCurriculumPlanRepository planRepo,
            JpaAssessmentScoreRepository assessmentScoreRepo,
            CriterionMapper mapper) {
        this.criterionRepo = criterionRepo;
        this.classGroupRepo = classGroupRepo;
        this.planRepo = planRepo;
        this.assessmentScoreRepo = assessmentScoreRepo;
        this.mapper = mapper;
    }

    @Override
    public boolean classGroupExists(UUID classGroupId) {
        return classGroupRepo.existsById(classGroupId);
    }

    @Override
    public boolean curriculumPlanExists(UUID curriculumPlanId) {
        return planRepo.existsById(curriculumPlanId);
    }

    @Override
    @Transactional
    public EvaluationCriterion create(
            UUID classGroupId,
            Integer trimester,
            String dimension,
            String name,
            String activityName,
            UUID curriculumPlanId) {
        EvaluationCriterionEntity e = new EvaluationCriterionEntity();
        e.setClassGroup(classGroupRepo.getReferenceById(classGroupId));
        e.setTrimester(trimester);
        e.setDimension(dimension);
        e.setName(name);
        e.setActivityName(activityName);
        if (curriculumPlanId != null) {
            e.setCurriculumPlan(planRepo.getReferenceById(curriculumPlanId));
        }
        e.setCreatedAt(LocalDateTime.now());
        return mapper.toDomain(criterionRepo.save(e));
    }

    @Override
    @Transactional
    public EvaluationCriterion update(UUID id, String name) {
        EvaluationCriterionEntity e =
                criterionRepo
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Criterion", id));
        if (name != null) e.setName(name);
        return mapper.toDomain(criterionRepo.save(e));
    }

    @Override
    public Optional<EvaluationCriterion> findById(UUID id) {
        return criterionRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<EvaluationCriterion> list(UUID classGroupId, Integer trimester, String dimension) {
        return criterionRepo.search(classGroupId, trimester, dimension).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        criterionRepo.deleteById(id);
    }

    @Override
    public boolean hasScoresForCriterion(UUID id) {
        return assessmentScoreRepo.existsByCriterion(id);
    }
}
