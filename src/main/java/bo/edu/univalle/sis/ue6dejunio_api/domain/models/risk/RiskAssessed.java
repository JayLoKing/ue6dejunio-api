package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import java.util.UUID;

/**
 * A student's standing prediction in one subject moved from one category to another.
 *
 * <p>Published only when the discrete level actually changed. The probability moves on every run —
 * a mark entered anywhere in the subject shifts it — and announcing that would announce noise. The
 * category is what the school can act on, and a category that did not move is not news.
 *
 * @param previousLevel null the first time this student and subject are ever predicted
 */
public record RiskAssessed(
    UUID predictionId,
    UUID studentId,
    UUID classGroupId,
    int trimester,
    RiskLevel previousLevel,
    RiskLevel currentLevel
) {
}
