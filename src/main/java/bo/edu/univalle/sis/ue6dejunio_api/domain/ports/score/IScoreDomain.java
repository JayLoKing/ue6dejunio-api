package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IScoreDomain {
    UUID ensureAcademicScore(
            UUID courseEnrollmentId, UUID classGroupId, Integer trimester, UUID createdBy);

    void setDimensions(
            UUID academicScoreId,
            BigDecimal being,
            BigDecimal knowing,
            BigDecimal doing,
            BigDecimal deciding);

    List<AcademicScore> findByCourseEnrollment(UUID courseEnrollmentId);

    List<AcademicScore> findByCourseEnrollmentIn(Collection<UUID> courseEnrollmentIds);

    Optional<AcademicScore> find(UUID courseEnrollmentId, UUID classGroupId, Integer trimester);
}
