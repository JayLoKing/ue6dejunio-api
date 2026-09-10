package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A prediction a run wants written.
 *
 * <p>Separate from {@link RiskPrediction}, which is what comes back out. The difference is the
 * feature vector: on the way in it is a {@link RiskFeatures}, the object the model was actually
 * given, and on the way out it is the JSON text the column holds. Turning one into the other is a
 * storage decision, so it belongs to the adapter — a use case that has to serialize before it can
 * save is a use case that has taken on a format it does not care about.
 *
 * <p>No id: which row this corrects is decided by the student, the subject and the trimester, which
 * is what the table's unique constraint says too.
 */
public record NewRiskPrediction(
    UUID studentId,
    UUID classGroupId,
    int trimester,
    RiskLevel riskLevel,
    BigDecimal pFail,
    BigDecimal pOutstanding,
    RiskFeatures features,
    LocalDateTime predictedAt
) {

    public NewRiskPrediction {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(classGroupId, "classGroupId");
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(pFail, "pFail");
        Objects.requireNonNull(pOutstanding, "pOutstanding");
        Objects.requireNonNull(features, "features");
        // The column is NOT NULL and its default only applies when the column is left out of the
        // statement, which JPA never does. A null here is not a timestamp the database fills in
        // later — it is a write that fails, and failing here says so while the run can still name
        // which prediction was being written.
        Objects.requireNonNull(predictedAt, "predictedAt");
    }
}
