package bo.edu.univalle.sis.ue6dejunio_api.application.services.score;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.RegisterScoreCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ScoreService implements IScoreService {

    private final IScoreDomain scoreDomain;

    public ScoreService(IScoreDomain scoreDomain) {
        this.scoreDomain = scoreDomain;
    }

    @Override
    @Transactional
    public AcademicScore register(RegisterScoreCommand command) {
        if (!scoreDomain.enrollmentExists(command.enrollmentId())) {
            throw new ResourceNotFoundException("Enrollment", command.enrollmentId());
        }
        return scoreDomain.upsert(command);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicScore> byEnrollment(UUID enrollmentId) {
        return scoreDomain.findByEnrollment(enrollmentId);
    }
}
