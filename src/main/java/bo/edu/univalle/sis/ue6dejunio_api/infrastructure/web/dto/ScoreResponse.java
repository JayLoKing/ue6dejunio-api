package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ScoreResponse(
    UUID id,
    UUID enrollmentId,
    Integer trimester,
    BigDecimal scoreBeing,
    BigDecimal scoreKnowing,
    BigDecimal scoreDoing,
    BigDecimal scoreDeciding,
    BigDecimal totalScore,
    UUID createdBy,
    LocalDateTime updatedAt
) {
    public static ScoreResponse from(AcademicScore s) {
        return new ScoreResponse(
            s.id(), s.enrollmentId(), s.trimester(),
            s.scoreBeing(), s.scoreKnowing(), s.scoreDoing(), s.scoreDeciding(),
            s.totalScore(), s.createdBy(), s.updatedAt()
        );
    }
}
