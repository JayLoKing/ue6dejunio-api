package bo.edu.univalle.sis.ue6dejunio_api.domain.models.score;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AcademicScore(
        UUID id,
        UUID courseEnrollmentId,
        UUID classGroupId,
        String subjectName,
        Integer trimester,
        BigDecimal scoreBeing,
        BigDecimal scoreKnowing,
        BigDecimal scoreDoing,
        BigDecimal scoreDeciding,
        BigDecimal totalScore,
        UUID createdBy,
        LocalDateTime updatedAt) {}
