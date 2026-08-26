package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.subject.ISubjectDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.SubjectEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentScoreRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaSubjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class SubjectRepositoryAdapter implements ISubjectDomain {

    private final JpaSubjectRepository subjectRepo;
    private final JpaClassGroupRepository classGroupRepo;
    private final JpaAssessmentScoreRepository assessmentScoreRepo;

    public SubjectRepositoryAdapter(JpaSubjectRepository subjectRepo, JpaClassGroupRepository classGroupRepo,
                                    JpaAssessmentScoreRepository assessmentScoreRepo) {
        this.subjectRepo = subjectRepo;
        this.classGroupRepo = classGroupRepo;
        this.assessmentScoreRepo = assessmentScoreRepo;
    }

    @Override
    @Transactional
    public Subject create(String name, boolean technical) {
        SubjectEntity e = new SubjectEntity();
        e.setName(name);
        e.setTechnical(technical);
        e.setActive(true);
        return toDomain(subjectRepo.save(e));
    }

    @Override
    @Transactional
    public Subject update(UUID id, String name, Boolean technical, Boolean active) {
        SubjectEntity e = subjectRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Subject", id));
        if (name != null) e.setName(name);
        if (technical != null) e.setTechnical(technical);
        if (active != null) e.setActive(active);
        return toDomain(subjectRepo.save(e));
    }

    @Override
    public Optional<Subject> findById(UUID id) {
        return subjectRepo.findById(id).map(this::toDomain);
    }

    @Override
    public boolean usedInClassGroups(UUID id) {
        return classGroupRepo.existsBySubject_Id(id);
    }

    @Override
    public Page<Subject> list(Pageable pageable) {
        return subjectRepo.findByActiveTrue(pageable).map(this::toDomain);
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

    private Subject toDomain(SubjectEntity e) {
        return new Subject(e.getId(), e.getName(), e.isTechnical(), e.isActive());
    }
}
