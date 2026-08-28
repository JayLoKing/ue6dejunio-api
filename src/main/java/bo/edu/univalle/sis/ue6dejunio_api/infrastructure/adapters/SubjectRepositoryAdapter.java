package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.subject.ISubjectDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.KnowledgeAreaEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.SubjectEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentScoreRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaKnowledgeAreaRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaSubjectRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class SubjectRepositoryAdapter implements ISubjectDomain {

    private final JpaSubjectRepository subjectRepo;
    private final JpaAssessmentScoreRepository assessmentScoreRepo;
    private final JpaKnowledgeAreaRepository areaRepo;

    public SubjectRepositoryAdapter(JpaSubjectRepository subjectRepo,
                                    JpaAssessmentScoreRepository assessmentScoreRepo,
                                    JpaKnowledgeAreaRepository areaRepo) {
        this.subjectRepo = subjectRepo;
        this.assessmentScoreRepo = assessmentScoreRepo;
        this.areaRepo = areaRepo;
    }

    @Override
    @Transactional
    public Subject create(String name, Integer areaId, boolean technical) {
        SubjectEntity e = new SubjectEntity();
        e.setName(name);
        // The area is not optional: the curriculum plan groups its blocks by it, so a subject that
        // belongs to none could never be printed.
        e.setArea(areaRepo.findById(areaId)
            .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArea", areaId)));
        e.setTechnical(technical);
        e.setActive(true);
        return toDomain(subjectRepo.save(e));
    }

    @Override
    @Transactional
    public Subject update(UUID id, String name, Integer areaId, Boolean technical, Boolean active) {
        SubjectEntity e = subjectRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Subject", id));
        if (name != null) {
            e.setName(name);
        }
        if (areaId != null) {
            e.setArea(areaRepo.findById(areaId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArea", areaId)));
        }
        if (technical != null) {
            e.setTechnical(technical);
        }
        if (active != null) {
            e.setActive(active);
        }
        return toDomain(subjectRepo.save(e));
    }

    @Override
    public Optional<Subject> findById(UUID id) {
        return subjectRepo.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<Subject> list(PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        return SpringPaging.toPageResult(
            subjectRepo.findByActiveTrue(pageable).map(this::toDomain));
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        SubjectEntity e = subjectRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Subject", id));
        e.setActive(false);
        subjectRepo.save(e);
    }

    @Override
    public boolean hasScoresForSubject(UUID id) {
        return assessmentScoreRepo.existsBySubject(id);
    }

    @Override
    public boolean areaExists(Integer areaId) {
        return areaId != null && areaRepo.existsById(areaId);
    }

    private Subject toDomain(SubjectEntity e) {
        KnowledgeAreaEntity area = e.getArea();
        return new Subject(e.getId(), e.getName(),
            area != null ? area.getId() : null,
            area != null ? area.getName() : null,
            e.isTechnical(), e.isActive());
    }
}
