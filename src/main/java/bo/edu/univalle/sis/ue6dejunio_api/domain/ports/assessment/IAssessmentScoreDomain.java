package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.CriterionAvg;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IAssessmentScoreDomain {
    AssessmentScore upsert(UUID courseEnrollmentId, UUID eventId, BigDecimal score);
    Optional<AssessmentScore> findById(UUID id);
    List<AssessmentScore> listByEvent(UUID eventId);
    List<AssessmentScore> listByCourseEnrollment(UUID courseEnrollmentId);
    List<CriterionAvg> criterionAveragesForConsolidation(UUID courseEnrollmentId, UUID classGroupId, Integer trimester);
    UUID courseOfCourseEnrollment(UUID courseEnrollmentId);
    void deleteById(UUID id);
}
