package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The single row a write answers with.
 *
 * <p>No names: the caller marking a prediction as handled had it on screen already, and adding the
 * join here would make a two-field write pay for a three-table read.
 */
public record RiskPredictionResponse(
    UUID id,
    UUID studentId,
    UUID classGroupId,
    Integer trimester,
    String riskLevel,
    BigDecimal pFail,
    BigDecimal pOutstanding,
    boolean attended,
    LocalDateTime predictedAt
) {

    public static RiskPredictionResponse from(RiskPrediction prediction) {
        return new RiskPredictionResponse(
            prediction.id(),
            prediction.studentId(),
            prediction.classGroupId(),
            prediction.trimester(),
            prediction.riskLevel().modelName(),
            prediction.pFail(),
            prediction.pOutstanding(),
            prediction.attended(),
            prediction.predictedAt());
    }
}
