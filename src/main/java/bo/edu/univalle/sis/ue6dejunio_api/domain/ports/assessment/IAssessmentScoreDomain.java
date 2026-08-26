package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.DimensionAvg;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IAssessmentScoreDomain {
    AssessmentScore upsertForEvent(UUID courseEnrollmentId, UUID eventId, BigDecimal score);

    AssessmentScore upsertForCriterion(UUID courseEnrollmentId, UUID criterionId, BigDecimal score);

    Optional<AssessmentScore> findById(UUID id);

    List<AssessmentScore> listByEvent(UUID eventId);

    /** Direct scores of one criterion across the whole roster, mirroring {@link #listByEvent}. */
    List<AssessmentScore> listByCriterion(UUID criterionId);

    List<AssessmentScore> listByCourseEnrollment(UUID courseEnrollmentId);

    /**
     * Average of each dimension over its criteria, not over its raw scores: every criterion first
     * averages its own activity items, and only then do the criteria average into the dimension.
     * A criterion with ten items therefore weighs the same as one scored directly.
     */
    List<DimensionAvg> dimensionAverages(UUID courseEnrollmentId, UUID classGroupId, Integer trimester);

    UUID courseOfCourseEnrollment(UUID courseEnrollmentId);

    void deleteById(UUID id);
}
