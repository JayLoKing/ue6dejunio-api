package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.RegisterScoreCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicScoreEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAcademicScoreRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaEnrollmentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class ScoreRepositoryAdapter implements IScoreDomain {

    private final JpaAcademicScoreRepository scoreRepo;
    private final JpaEnrollmentRepository enrollmentRepo;

    public ScoreRepositoryAdapter(JpaAcademicScoreRepository scoreRepo,
                                  JpaEnrollmentRepository enrollmentRepo) {
        this.scoreRepo = scoreRepo;
        this.enrollmentRepo = enrollmentRepo;
    }

    @Override
    public boolean enrollmentExists(UUID enrollmentId) {
        return enrollmentRepo.existsById(enrollmentId);
    }

    @Override
    @Transactional
    public AcademicScore upsert(RegisterScoreCommand c) {
        AcademicScoreEntity e = scoreRepo
            .findByEnrollment_IdAndTrimester(c.enrollmentId(), c.trimester())
            .orElseGet(() -> {
                AcademicScoreEntity n = new AcademicScoreEntity();
                n.setEnrollment(enrollmentRepo.getReferenceById(c.enrollmentId()));
                n.setTrimester(c.trimester());
                n.setCreatedBy(c.createdBy());
                return n;
            });
        e.setScoreBeing(c.scoreBeing());
        e.setScoreKnowing(c.scoreKnowing());
        e.setScoreDoing(c.scoreDoing());
        e.setScoreDeciding(c.scoreDeciding());
        e.setUpdatedAt(LocalDateTime.now());
        AcademicScoreEntity saved = scoreRepo.saveAndFlush(e);
        return toDomain(saved);
    }

    @Override
    public List<AcademicScore> findByEnrollment(UUID enrollmentId) {
        return scoreRepo.findByEnrollment_IdOrderByTrimester(enrollmentId).stream()
            .map(this::toDomain).toList();
    }

    private AcademicScore toDomain(AcademicScoreEntity e) {
        return new AcademicScore(
            e.getId(), e.getEnrollment().getId(), e.getTrimester(),
            e.getScoreBeing(), e.getScoreKnowing(), e.getScoreDoing(), e.getScoreDeciding(),
            e.getTotalScore(), e.getCreatedBy(), e.getUpdatedAt()
        );
    }
}
