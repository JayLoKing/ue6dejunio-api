package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ClassGroupEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCurriculumPlanRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class PdcRepositoryAdapter implements IPdcDomain {

    private final JpaCurriculumPlanRepository pdcRepo;
    private final JpaClassGroupRepository classGroupRepo;
    private final JpaUserRepository userRepo;

    public PdcRepositoryAdapter(JpaCurriculumPlanRepository pdcRepo,
                                JpaClassGroupRepository classGroupRepo,
                                JpaUserRepository userRepo) {
        this.pdcRepo = pdcRepo;
        this.classGroupRepo = classGroupRepo;
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public Pdc save(Pdc pdc) {
        CurriculumPlanEntity e = pdc.getId() == null
            ? new CurriculumPlanEntity()
            : pdcRepo.findById(pdc.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PDC", pdc.getId()));

        if (pdc.getId() == null) {
            ClassGroupEntity cg = classGroupRepo.findById(pdc.getClassGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("ClassGroup", pdc.getClassGroupId()));
            e.setClassGroup(cg);
            e.setTrimester(pdc.getTrimester());
            e.setCreatedAt(LocalDateTime.now());
            if (pdc.getCreatedById() != null) {
                e.setCreatedBy(userRepo.getReferenceById(pdc.getCreatedById()));
            }
        }
        e.setStatus(pdc.getStatus());
        e.setReviewObservations(pdc.getReviewObservations());
        e.setTitle(pdc.getTitle());
        e.setHolisticObjective(pdc.getHolisticObjective());
        e.setLearningObjective(pdc.getLearningObjective());
        e.setContents(pdc.getContents());
        e.setPracticeActivities(pdc.getPracticeActivities());
        e.setTheoryActivities(pdc.getTheoryActivities());
        e.setValuationActivities(pdc.getValuationActivities());
        e.setProductionActivities(pdc.getProductionActivities());
        e.setResources(pdc.getResources());
        e.setStartDate(pdc.getStartDate());
        e.setEndDate(pdc.getEndDate());
        e.setCriteriaBeing(pdc.getCriteriaBeing());
        e.setCriteriaKnowing(pdc.getCriteriaKnowing());
        e.setCriteriaDoing(pdc.getCriteriaDoing());
        e.setCriteriaDeciding(pdc.getCriteriaDeciding());
        if (pdc.getUpdatedById() != null) {
            e.setUpdatedBy(userRepo.getReferenceById(pdc.getUpdatedById()));
        }
        e.setUpdatedAt(LocalDateTime.now());
        return toDomain(pdcRepo.save(e));
    }

    @Override
    public Optional<Pdc> findById(UUID id) {
        return pdcRepo.findById(id).map(this::toDomain);
    }

    @Override
    public boolean classGroupExists(UUID classGroupId) {
        return classGroupRepo.existsById(classGroupId);
    }

    @Override
    public boolean existsByClassGroupAndTrimester(UUID classGroupId, Integer trimester) {
        return pdcRepo.existsByClassGroup_IdAndTrimester(classGroupId, trimester);
    }

    @Override
    public UUID teacherIdOfClassGroup(UUID classGroupId) {
        return classGroupRepo.findById(classGroupId)
            .map(cg -> cg.getTeacher() != null ? cg.getTeacher().getId() : null)
            .orElseThrow(() -> new ResourceNotFoundException("ClassGroup", classGroupId));
    }

    @Override
    public Page<Pdc> list(UUID classGroupId, Integer trimester, String status, Pageable pageable) {
        return pdcRepo.search(classGroupId, trimester, status, pageable).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        pdcRepo.deleteById(id);
    }

    private Pdc toDomain(CurriculumPlanEntity e) {
        ClassGroupEntity cg = e.getClassGroup();
        return Pdc.builder()
            .id(e.getId())
            .classGroupId(cg != null ? cg.getId() : null)
            .subjectName(cg != null && cg.getSubject() != null ? cg.getSubject().getName() : null)
            .teacherName(cg != null && cg.getTeacher() != null
                ? cg.getTeacher().getNames() + " " + cg.getTeacher().getLastNames() : null)
            .createdById(e.getCreatedBy() != null ? e.getCreatedBy().getId() : null)
            .updatedById(e.getUpdatedBy() != null ? e.getUpdatedBy().getId() : null)
            .updatedByName(e.getUpdatedBy() != null
                ? e.getUpdatedBy().getNames() + " " + e.getUpdatedBy().getLastNames() : null)
            .trimester(e.getTrimester())
            .status(e.getStatus())
            .reviewObservations(e.getReviewObservations())
            .title(e.getTitle())
            .holisticObjective(e.getHolisticObjective())
            .learningObjective(e.getLearningObjective())
            .contents(e.getContents())
            .practiceActivities(e.getPracticeActivities())
            .theoryActivities(e.getTheoryActivities())
            .valuationActivities(e.getValuationActivities())
            .productionActivities(e.getProductionActivities())
            .resources(e.getResources())
            .startDate(e.getStartDate())
            .endDate(e.getEndDate())
            .criteriaBeing(e.getCriteriaBeing())
            .criteriaKnowing(e.getCriteriaKnowing())
            .criteriaDoing(e.getCriteriaDoing())
            .criteriaDeciding(e.getCriteriaDeciding())
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .build();
    }
}
