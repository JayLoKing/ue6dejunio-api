package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.DimensionAvg;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AssessmentScoreEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentEventRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentScoreRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseEnrollmentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaEvaluationCriterionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AssessmentScoreRepositoryAdapter implements IAssessmentScoreDomain {

    private final JpaAssessmentScoreRepository scoreRepo;
    private final JpaAssessmentEventRepository eventRepo;
    private final JpaEvaluationCriterionRepository criterionRepo;
    private final JpaCourseEnrollmentRepository enrollmentRepo;

    public AssessmentScoreRepositoryAdapter(
            JpaAssessmentScoreRepository scoreRepo,
            JpaAssessmentEventRepository eventRepo,
            JpaEvaluationCriterionRepository criterionRepo,
            JpaCourseEnrollmentRepository enrollmentRepo) {
        this.scoreRepo = scoreRepo;
        this.eventRepo = eventRepo;
        this.criterionRepo = criterionRepo;
        this.enrollmentRepo = enrollmentRepo;
    }

    @Override
    @Transactional
    public AssessmentScore upsertForEvent(UUID courseEnrollmentId, UUID eventId, BigDecimal score) {
        AssessmentScoreEntity e =
                scoreRepo
                        .findByCourseEnrollment_IdAndEvent_Id(courseEnrollmentId, eventId)
                        .orElseGet(
                                () -> {
                                    AssessmentScoreEntity n = new AssessmentScoreEntity();
                                    n.setCourseEnrollment(
                                            enrollmentRepo.getReferenceById(courseEnrollmentId));
                                    n.setEvent(eventRepo.getReferenceById(eventId));
                                    return n;
                                });
        e.setScore(score);
        return toDomain(scoreRepo.saveAndFlush(e));
    }

    @Override
    @Transactional
    public AssessmentScore upsertForCriterion(
            UUID courseEnrollmentId, UUID criterionId, BigDecimal score) {
        AssessmentScoreEntity e =
                scoreRepo
                        .findByCourseEnrollment_IdAndCriterion_Id(courseEnrollmentId, criterionId)
                        .orElseGet(
                                () -> {
                                    AssessmentScoreEntity n = new AssessmentScoreEntity();
                                    n.setCourseEnrollment(
                                            enrollmentRepo.getReferenceById(courseEnrollmentId));
                                    n.setCriterion(criterionRepo.getReferenceById(criterionId));
                                    return n;
                                });
        e.setScore(score);
        return toDomain(scoreRepo.saveAndFlush(e));
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
    public List<AssessmentScore> listByCriterion(UUID criterionId) {
        return scoreRepo.findByCriterion_Id(criterionId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<AssessmentScore> listByCourseEnrollment(UUID courseEnrollmentId) {
        return scoreRepo.findByCourseEnrollment_Id(courseEnrollmentId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<DimensionAvg> dimensionAverages(
            UUID courseEnrollmentId, UUID classGroupId, Integer trimester) {
        return scoreRepo.dimensionAverageRows(courseEnrollmentId, classGroupId, trimester).stream()
                .map(
                        r ->
                                new DimensionAvg(
                                        (String) r[0],
                                        r[1] != null ? new BigDecimal(r[1].toString()) : null))
                .toList();
    }

    @Override
    public UUID courseOfCourseEnrollment(UUID courseEnrollmentId) {
        return enrollmentRepo
                .findById(courseEnrollmentId)
                .map(en -> en.getCourse().getId())
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "CourseEnrollment", courseEnrollmentId));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        scoreRepo.deleteById(id);
        // The caller reconsolidates right after, through a native query. Flush here so that query
        // reads the row as gone instead of relying on Hibernate to infer it from raw SQL.
        scoreRepo.flush();
    }

    private AssessmentScore toDomain(AssessmentScoreEntity e) {
        return new AssessmentScore(
                e.getId(),
                e.getCourseEnrollment().getId(),
                e.getEvent() != null ? e.getEvent().getId() : null,
                e.getCriterion() != null ? e.getCriterion().getId() : null,
                e.getScore(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
