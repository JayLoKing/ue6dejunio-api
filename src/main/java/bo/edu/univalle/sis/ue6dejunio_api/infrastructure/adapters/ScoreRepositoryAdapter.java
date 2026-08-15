package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicScoreEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAcademicScoreRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseEnrollmentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class ScoreRepositoryAdapter implements IScoreDomain {

    private final JpaAcademicScoreRepository scoreRepo;
    private final JpaCourseEnrollmentRepository enrollmentRepo;
    private final JpaClassGroupRepository classGroupRepo;

    public ScoreRepositoryAdapter(JpaAcademicScoreRepository scoreRepo,
                                  JpaCourseEnrollmentRepository enrollmentRepo,
                                  JpaClassGroupRepository classGroupRepo) {
        this.scoreRepo = scoreRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.classGroupRepo = classGroupRepo;
    }

    @Override
    @Transactional
    public UUID ensureAcademicScore(UUID courseEnrollmentId, UUID classGroupId, Integer trimester, UUID createdBy) {
        AcademicScoreEntity e = scoreRepo
            .findByCourseEnrollment_IdAndClassGroup_IdAndTrimester(courseEnrollmentId, classGroupId, trimester)
            .orElseGet(() -> {
                AcademicScoreEntity n = new AcademicScoreEntity();
                n.setCourseEnrollment(enrollmentRepo.getReferenceById(courseEnrollmentId));
                n.setClassGroup(classGroupRepo.getReferenceById(classGroupId));
                n.setTrimester(trimester);
                n.setCreatedBy(createdBy);
                n.setScoreBeing(BigDecimal.ZERO);
                n.setScoreKnowing(BigDecimal.ZERO);
                n.setScoreDoing(BigDecimal.ZERO);
                n.setScoreDeciding(BigDecimal.ZERO);
                n.setUpdatedAt(LocalDateTime.now());
                return scoreRepo.saveAndFlush(n);
            });
        return e.getId();
    }

    @Override
    @Transactional
    public void setDimensions(UUID academicScoreId, BigDecimal being, BigDecimal knowing,
                              BigDecimal doing, BigDecimal deciding) {
        AcademicScoreEntity e = scoreRepo.findById(academicScoreId)
            .orElseThrow(() -> new ResourceNotFoundException("AcademicScore", academicScoreId));
        e.setScoreBeing(being);
        e.setScoreKnowing(knowing);
        e.setScoreDoing(doing);
        e.setScoreDeciding(deciding);
        e.setUpdatedAt(LocalDateTime.now());
        scoreRepo.saveAndFlush(e);
    }

    @Override
    public List<AcademicScore> findByCourseEnrollment(UUID courseEnrollmentId) {
        return scoreRepo.findByCourseEnrollment_IdOrderByClassGroup_Subject_NameAscTrimesterAsc(courseEnrollmentId)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public List<AcademicScore> findByCourseEnrollmentIn(Collection<UUID> courseEnrollmentIds) {
        if (courseEnrollmentIds.isEmpty()) {
            return List.of();
        }
        return scoreRepo
            .findByCourseEnrollment_IdInOrderByClassGroup_Subject_NameAscTrimesterAsc(courseEnrollmentIds)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<AcademicScore> find(UUID courseEnrollmentId, UUID classGroupId, Integer trimester) {
        return scoreRepo.findByCourseEnrollment_IdAndClassGroup_IdAndTrimester(
            courseEnrollmentId, classGroupId, trimester).map(this::toDomain);
    }

    private AcademicScore toDomain(AcademicScoreEntity e) {
        String subject = e.getClassGroup() != null && e.getClassGroup().getSubject() != null
            ? e.getClassGroup().getSubject().getName() : null;
        return new AcademicScore(
            e.getId(), e.getCourseEnrollment().getId(), e.getClassGroup().getId(), subject,
            e.getTrimester(), e.getScoreBeing(), e.getScoreKnowing(), e.getScoreDoing(), e.getScoreDeciding(),
            e.getTotalScore(), e.getCreatedBy(), e.getUpdatedAt());
    }
}
