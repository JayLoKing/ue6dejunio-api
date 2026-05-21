package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.RegisterScoreCommand;

import java.util.List;
import java.util.UUID;

public interface IScoreDomain {
    boolean enrollmentExists(UUID enrollmentId);
    AcademicScore upsert(RegisterScoreCommand command);
    List<AcademicScore> findByEnrollment(UUID enrollmentId);
}
