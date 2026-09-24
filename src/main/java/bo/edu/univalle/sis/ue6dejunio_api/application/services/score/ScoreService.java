package bo.edu.univalle.sis.ue6dejunio_api.application.services.score;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoreService implements IScoreService {

    private final IScoreDomain scoreDomain;

    public ScoreService(IScoreDomain scoreDomain) {
        this.scoreDomain = scoreDomain;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicScore> byCourseEnrollment(UUID courseEnrollmentId) {
        return scoreDomain.findByCourseEnrollment(courseEnrollmentId);
    }
}
