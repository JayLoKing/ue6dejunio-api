package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.UpdateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumAdaptationEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCurriculumAdaptationRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCurriculumPlanRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaStudentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class AdaptationRepositoryAdapter implements IAdaptationDomain {

    private final JpaCurriculumAdaptationRepository adaptationRepo;
    private final JpaCurriculumPlanRepository planRepo;
    private final JpaStudentRepository studentRepo;
    private final JpaUserRepository userRepo;

    public AdaptationRepositoryAdapter(JpaCurriculumAdaptationRepository adaptationRepo,
                                       JpaCurriculumPlanRepository planRepo,
                                       JpaStudentRepository studentRepo,
                                       JpaUserRepository userRepo) {
        this.adaptationRepo = adaptationRepo;
        this.planRepo = planRepo;
        this.studentRepo = studentRepo;
        this.userRepo = userRepo;
    }

    @Override
    public boolean planExists(UUID planId) {
        return planRepo.existsById(planId);
    }

    @Override
    public boolean studentExists(UUID studentId) {
        return studentRepo.existsById(studentId);
    }

    @Override
    public boolean existsByPlanAndStudent(UUID planId, UUID studentId) {
        return adaptationRepo.existsByCurriculumPlan_IdAndStudent_Id(planId, studentId);
    }

    @Override
    @Transactional
    public Adaptation create(CreateAdaptationCommand c) {
        CurriculumAdaptationEntity e = new CurriculumAdaptationEntity();
        e.setCurriculumPlan(planRepo.getReferenceById(c.planId()));
        e.setStudent(studentRepo.getReferenceById(c.studentId()));
        e.setAdaptedContents(c.adaptedContents());
        e.setAdaptedMethodology(c.adaptedMethodology());
        e.setAdaptedCriteria(c.adaptedCriteria());
        if (c.createdBy() != null) {
            UserEntity u = userRepo.getReferenceById(c.createdBy());
            e.setCreatedBy(u);
            e.setUpdatedBy(u);
        }
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return toDomain(adaptationRepo.save(e));
    }

    @Override
    @Transactional
    public Adaptation update(UUID id, UpdateAdaptationCommand c) {
        CurriculumAdaptationEntity e = adaptationRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Adaptacion", id));
        if (c.adaptedContents() != null) e.setAdaptedContents(c.adaptedContents());
        if (c.adaptedMethodology() != null) e.setAdaptedMethodology(c.adaptedMethodology());
        if (c.adaptedCriteria() != null) e.setAdaptedCriteria(c.adaptedCriteria());
        if (c.updatedBy() != null) {
            e.setUpdatedBy(userRepo.getReferenceById(c.updatedBy()));
        }
        e.setUpdatedAt(LocalDateTime.now());
        return toDomain(adaptationRepo.save(e));
    }

    @Override
    public Optional<Adaptation> findById(UUID id) {
        return adaptationRepo.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<Adaptation> listByPlan(UUID planId, PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        return SpringPaging.toPageResult(
            adaptationRepo.findByCurriculumPlan_Id(planId, pageable).map(this::toDomain));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        adaptationRepo.deleteById(id);
    }

    private Adaptation toDomain(CurriculumAdaptationEntity e) {
        StudentEntity s = e.getStudent();
        return new Adaptation(
            e.getId(),
            e.getCurriculumPlan() != null ? e.getCurriculumPlan().getId() : null,
            s != null ? s.getId() : null,
            s != null ? s.getNames() + " " + s.getLastNames() : null,
            e.getAdaptedContents(), e.getAdaptedMethodology(), e.getAdaptedCriteria(),
            e.getCreatedBy() != null ? e.getCreatedBy().getId() : null,
            e.getUpdatedBy() != null ? e.getUpdatedBy().getId() : null,
            e.getCreatedAt(), e.getUpdatedAt());
    }
}
