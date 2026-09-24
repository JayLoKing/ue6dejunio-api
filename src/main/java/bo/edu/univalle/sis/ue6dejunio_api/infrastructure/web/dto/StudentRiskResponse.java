package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskPrediction;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One standing prediction, with the two names that make it readable.
 *
 * <p>The names travel with it rather than being looked up afterwards. A prediction on its own is
 * two uuids, and a panel handed those resolves every one of them — thirty rows becoming thirty
 * requests, after the query behind this already selected the names in the same statement.
 *
 * @param riskLevel the model's spelling, {@code RiesgoCritico} and not the enum constant. It is
 *     what the model answers and what the column stores, so the web reads one vocabulary for these
 *     four categories instead of three.
 * @param pFail probability of failing. Kept apart from {@code pOutstanding} because the school
 *     reads two opposite questions off the same four class probabilities, and one collapsed score
 *     would name neither.
 */
public record StudentRiskResponse(
        UUID id,
        UUID studentId,
        String studentName,
        UUID classGroupId,
        String subjectName,
        Integer trimester,
        String riskLevel,
        BigDecimal pFail,
        BigDecimal pOutstanding,
        boolean attended,
        LocalDateTime predictedAt) {

    public static StudentRiskResponse from(StudentRisk risk) {
        RiskPrediction prediction = risk.prediction();
        return new StudentRiskResponse(
                prediction.id(),
                prediction.studentId(),
                risk.studentFullName(),
                prediction.classGroupId(),
                risk.subjectName(),
                prediction.trimester(),
                prediction.riskLevel().modelName(),
                prediction.pFail(),
                prediction.pOutstanding(),
                prediction.attended(),
                prediction.predictedAt());
    }
}
