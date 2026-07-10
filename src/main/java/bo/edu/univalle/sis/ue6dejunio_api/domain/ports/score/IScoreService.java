package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;

import java.util.List;
import java.util.UUID;

public interface IScoreService {
    List<AcademicScore> byCourseEnrollment(UUID courseEnrollmentId);
}
