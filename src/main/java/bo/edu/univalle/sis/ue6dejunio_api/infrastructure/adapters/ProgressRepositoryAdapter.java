package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.PlanProgress;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.progress.IProgressDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanProgressEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers.ProgressMapper;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCurriculumPlanRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaPlanProgressRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class ProgressRepositoryAdapter implements IProgressDomain {

    private final JpaPlanProgressRepository progressRepo;
    private final JpaCurriculumPlanRepository planRepo;
    private final ProgressMapper mapper;

    public ProgressRepositoryAdapter(JpaPlanProgressRepository progressRepo,
                                     JpaCurriculumPlanRepository planRepo,
                                     ProgressMapper mapper) {
        this.progressRepo = progressRepo;
        this.planRepo = planRepo;
        this.mapper = mapper;
    }

    @Override
    public boolean planExists(UUID planId) {
        return planRepo.existsById(planId);
    }

    @Override
    @Transactional
    public PlanProgress create(UUID planId, LocalDate date, String content, BigDecimal pct, String obs, UUID createdBy) {
        CurriculumPlanProgressEntity e = new CurriculumPlanProgressEntity();
        e.setCurriculumPlan(planRepo.getReferenceById(planId));
        e.setProgressDate(date != null ? date : LocalDate.now());
        e.setAdvancedContent(content);
        e.setPercentage(pct);
        e.setObservations(obs);
        e.setCreatedBy(createdBy);
        e.setCreatedAt(LocalDateTime.now());
        return mapper.toDomain(progressRepo.save(e));
    }

    @Override
    public List<PlanProgress> listByPlan(UUID planId) {
        return progressRepo.findByCurriculumPlan_IdOrderByProgressDateDesc(planId)
            .stream().map(mapper::toDomain).toList();
    }
}
