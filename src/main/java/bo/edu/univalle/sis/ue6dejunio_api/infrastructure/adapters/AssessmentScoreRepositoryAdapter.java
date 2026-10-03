package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.DimensionAvg;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.ScoreAuthor;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AssessmentScoreEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentEventRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAssessmentScoreRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseEnrollmentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaEvaluationCriterionRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class AssessmentScoreRepositoryAdapter implements IAssessmentScoreDomain {

    private static final String STATUS_WITHDRAWN = "Withdrawn";

    private final JpaAssessmentScoreRepository scoreRepo;
    private final JpaAssessmentEventRepository eventRepo;
    private final JpaEvaluationCriterionRepository criterionRepo;
    private final JpaCourseEnrollmentRepository enrollmentRepo;
    private final JpaUserRepository userRepo;

    public AssessmentScoreRepositoryAdapter(
            JpaAssessmentScoreRepository scoreRepo,
            JpaAssessmentEventRepository eventRepo,
            JpaEvaluationCriterionRepository criterionRepo,
            JpaCourseEnrollmentRepository enrollmentRepo,
            JpaUserRepository userRepo) {
        this.scoreRepo = scoreRepo;
        this.eventRepo = eventRepo;
        this.criterionRepo = criterionRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public AssessmentScore upsertForEvent(
            UUID courseEnrollmentId, UUID eventId, BigDecimal score, UUID actorId) {
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
        stampAuthor(e, actorId);
        return one(scoreRepo.saveAndFlush(e));
    }

    @Override
    @Transactional
    public AssessmentScore upsertForCriterion(
            UUID courseEnrollmentId, UUID criterionId, BigDecimal score, UUID actorId) {
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
        stampAuthor(e, actorId);
        return one(scoreRepo.saveAndFlush(e));
    }

    @Override
    public Optional<AssessmentScore> findById(UUID id) {
        return scoreRepo.findById(id).map(this::one);
    }

    @Override
    public List<AssessmentScore> listByEvent(UUID eventId) {
        return toDomains(scoreRepo.findByEvent_Id(eventId));
    }

    @Override
    public List<AssessmentScore> listByCriterion(UUID criterionId) {
        return toDomains(scoreRepo.findByCriterion_Id(criterionId));
    }

    @Override
    public List<AssessmentScore> listByCourseEnrollment(UUID courseEnrollmentId) {
        return toDomains(scoreRepo.findByCourseEnrollment_Id(courseEnrollmentId));
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

    /**
     * Same lookup {@link #courseOfCourseEnrollment} just did, and inside the same transaction, so
     * the persistence context answers it without a second statement.
     */
    @Override
    public boolean courseEnrollmentIsWithdrawn(UUID courseEnrollmentId) {
        return enrollmentRepo
                .findById(courseEnrollmentId)
                .map(en -> STATUS_WITHDRAWN.equals(en.getStatus()))
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

    /**
     * A new row names the actor as creator and last editor; an existing one only moves the last
     * editor, so the first author survives a later edit by someone else.
     */
    private static void stampAuthor(AssessmentScoreEntity e, UUID actorId) {
        if (e.getId() == null) {
            e.setCreatedBy(actorId);
        }
        e.setUpdatedBy(actorId);
    }

    private AssessmentScore one(AssessmentScoreEntity e) {
        return toDomains(List.of(e)).getFirst();
    }

    /**
     * Maps a whole result with one extra query for the names of every author in it, never one per
     * row: a roster of N scores costs the same two statements as a roster of one.
     */
    private List<AssessmentScore> toDomains(List<AssessmentScoreEntity> rows) {
        Map<UUID, ScoreAuthor> authors = authorsOf(rows);
        return rows.stream().map(e -> toDomain(e, authors)).toList();
    }

    private Map<UUID, ScoreAuthor> authorsOf(List<AssessmentScoreEntity> rows) {
        Set<UUID> ids = new HashSet<>();
        for (AssessmentScoreEntity e : rows) {
            if (e.getCreatedBy() != null) {
                ids.add(e.getCreatedBy());
            }
            if (e.getUpdatedBy() != null) {
                ids.add(e.getUpdatedBy());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepo.findAllById(ids).stream()
                .collect(
                        Collectors.toMap(
                                UserEntity::getId, AssessmentScoreRepositoryAdapter::author));
    }

    private static ScoreAuthor author(UserEntity u) {
        return new ScoreAuthor(u.getId(), u.getNames() + " " + u.getLastNames());
    }

    private static ScoreAuthor authorOf(UUID id, Map<UUID, ScoreAuthor> authors) {
        return id == null ? null : authors.getOrDefault(id, new ScoreAuthor(id, null));
    }

    private AssessmentScore toDomain(AssessmentScoreEntity e, Map<UUID, ScoreAuthor> authors) {
        return new AssessmentScore(
                e.getId(),
                e.getCourseEnrollment().getId(),
                e.getEvent() != null ? e.getEvent().getId() : null,
                e.getCriterion() != null ? e.getCriterion().getId() : null,
                e.getScore(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                authorOf(e.getCreatedBy(), authors),
                authorOf(e.getUpdatedBy(), authors));
    }
}
