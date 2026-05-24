package bo.edu.univalle.sis.ue6dejunio_api.application.services.score;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.RegisterScoreByStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.RegisterScoreCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ScoreService implements IScoreService {

    private final IScoreDomain scoreDomain;
    private final IEnrollmentDomain enrollmentDomain;

    public ScoreService(IScoreDomain scoreDomain, IEnrollmentDomain enrollmentDomain) {
        this.scoreDomain = scoreDomain;
        this.enrollmentDomain = enrollmentDomain;
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
    @Transactional
    public AcademicScore registerByStudent(RegisterScoreByStudentCommand c) {
        UUID enrollmentId = enrollmentDomain.findEnrollmentId(c.studentId(), c.classGroupId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Enrollment (student+classGroup)",
                "student=" + c.studentId() + " classGroup=" + c.classGroupId()));
        return scoreDomain.upsert(new RegisterScoreCommand(
            enrollmentId, c.trimester(),
            c.scoreBeing(), c.scoreKnowing(), c.scoreDoing(), c.scoreDeciding(),
            c.createdBy()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AcademicScore> byEnrollment(UUID enrollmentId) {
        return scoreDomain.findByEnrollment(enrollmentId);
    }
}
