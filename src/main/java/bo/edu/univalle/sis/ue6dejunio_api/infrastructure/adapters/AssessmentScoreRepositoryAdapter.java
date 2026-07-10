package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.CriterionAvg;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AssessmentScoreEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentEventRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentScoreRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseEnrollmentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class AssessmentScoreRepositoryAdapter implements IAssessmentScoreDomain {

    private final JpaAssessmentScoreRepository scoreRepo;
    private final JpaAssessmentEventRepository eventRepo;
    private final JpaCourseEnrollmentRepository enrollmentRepo;

    public AssessmentScoreRepositoryAdapter(JpaAssessmentScoreRepository scoreRepo,
                                            JpaAssessmentEventRepository eventRepo,
                                            JpaCourseEnrollmentRepository enrollmentRepo) {
        this.scoreRepo = scoreRepo;
        this.eventRepo = eventRepo;
        this.enrollmentRepo = enrollmentRepo;
    }

    @Override
    @Transactional
    public AssessmentScore upsert(UUID courseEnrollmentId, UUID eventId, BigDecimal score) {
        AssessmentScoreEntity e = scoreRepo
            .findByCourseEnrollment_IdAndEvent_Id(courseEnrollmentId, eventId)
            .orElseGet(() -> {
                AssessmentScoreEntity n = new AssessmentScoreEntity();
                n.setCourseEnrollment(enrollmentRepo.getReferenceById(courseEnrollmentId));
                n.setEvent(eventRepo.getReferenceById(eventId));
                n.setCreatedAt(LocalDateTime.now());
                return n;
            });
        e.setScore(score);
        e.setUpdatedAt(LocalDateTime.now());
        return toDomain(scoreRepo.save(e));
    }

    @Override
    public Optional<AssessmentScore> findById(UUID id) {
        return scoreRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<AssessmentScore> listByEvent(UUID eventId) {
        return scoreRepo.findByEvent_Id(eventId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<AssessmentScore> listByCourseEnrollment(UUID courseEnrollmentId) {
        return scoreRepo.findByCourseEnrollment_Id(courseEnrollmentId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<CriterionAvg> criterionAveragesForConsolidation(UUID courseEnrollmentId,
                                                                UUID classGroupId, Integer trimester) {
        return scoreRepo.consolidationRows(courseEnrollmentId, classGroupId, trimester).stream()
            .map(r -> new CriterionAvg(
                (String) r[0],
                (BigDecimal) r[1],
                r[2] != null ? new BigDecimal(r[2].toString()) : null))
            .toList();
    }

    @Override
    public UUID courseOfCourseEnrollment(UUID courseEnrollmentId) {
        return enrollmentRepo.findById(courseEnrollmentId)
            .map(en -> en.getCourse().getId())
            .orElseThrow(() -> new ResourceNotFoundException("CourseEnrollment", courseEnrollmentId));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        scoreRepo.deleteById(id);
    }

    private AssessmentScore toDomain(AssessmentScoreEntity e) {
        return new AssessmentScore(
            e.getId(), e.getCourseEnrollment().getId(), e.getEvent().getId(), e.getScore());
    }
}
