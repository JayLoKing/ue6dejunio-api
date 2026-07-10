package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.SetScoreCommand;

import java.util.List;
import java.util.UUID;

public interface IAssessmentScoreService {
    AssessmentScore setScore(SetScoreCommand command);
    List<AssessmentScore> listByEvent(UUID eventId);
    List<AssessmentScore> listByCourseEnrollment(UUID courseEnrollmentId);
    void delete(UUID id);
}
