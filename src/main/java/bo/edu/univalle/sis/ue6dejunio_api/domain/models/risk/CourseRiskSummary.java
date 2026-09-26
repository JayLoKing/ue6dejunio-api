package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import java.util.UUID;

/**
 * How many students of one classroom sit in each risk band.
 *
 * <p>STUDENTS, NOT PREDICTIONS, and that is the whole decision in this record. The model predicts
 * one row per subject, so a child failing three areas leaves three RiesgoCritico rows; counted as
 * rows, a classroom of twenty would report thirty-one "at risk" and the Director would be reading a
 * number that cannot mean what it says. Each student is counted once, in their worst band, because
 * that is the band somebody has to act on.
 *
 * @param courseId the classroom these counts belong to
 * @param gradeName and
 * @param parallelName how the school says the classroom out loud, carried so the reader does not
 *     resolve the id against a second listing
 * @param critical students whose worst subject is {@link RiskLevel#RIESGO_CRITICO}
 * @param atRisk worst subject {@link RiskLevel#EN_RIESGO}
 * @param safe worst subject {@link RiskLevel#SIN_RIESGO}
 * @param outstanding every predicted subject {@link RiskLevel#SOBRESALIENTE}
 * @param unpredicted enrolled students the model has not scored this trimester. Stated rather than
 *     left out: a classroom where four of twenty were predicted is not a safe classroom, it is one
 *     the sweep has barely reached, and the four bands alone would read as the whole room
 */
public record CourseRiskSummary(
        UUID courseId,
        String gradeName,
        String parallelName,
        int critical,
        int atRisk,
        int safe,
        int outstanding,
        int unpredicted) {}
