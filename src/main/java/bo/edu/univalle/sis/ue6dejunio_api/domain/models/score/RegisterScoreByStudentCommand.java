package bo.edu.univalle.sis.ue6dejunio_api.domain.models.score;

import java.math.BigDecimal;
import java.util.UUID;

public record RegisterScoreByStudentCommand(
    UUID studentId,
    UUID classGroupId,
    Integer trimester,
    BigDecimal scoreBeing,
    BigDecimal scoreKnowing,
    BigDecimal scoreDoing,
    BigDecimal scoreDeciding,
    UUID createdBy
) {}
