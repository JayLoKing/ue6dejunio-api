package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The standing prediction for one student, in one subject, for one trimester.
 *
 * <p>Standing, not historical: re-running the model corrects this row rather than appending beside
 * it. Whoever needs to know that a student got worse has to read the row before overwriting it,
 * which is what {@code IRiskPredictionDomain.upsert} returns.
 *
 * @param featuresAnalyzed the exact vector the model was given, as JSON. Without it the prediction
 *                         cannot be explained a week later, when the inputs have all moved.
 * @param attended         whether somebody acted on this. The only field here a person writes.
 */
public record RiskPrediction(
    UUID id,
    UUID studentId,
    UUID classGroupId,
    Integer trimester,
    RiskLevel riskLevel,
    BigDecimal pFail,
    BigDecimal pOutstanding,
    boolean attended,
    String featuresAnalyzed,
    LocalDateTime predictedAt
) {
}
