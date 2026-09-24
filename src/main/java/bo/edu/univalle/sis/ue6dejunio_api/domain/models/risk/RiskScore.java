package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What the model answered for one feature vector.
 *
 * <p>Two probabilities rather than one, because the school reads two opposite questions off the
 * same four class probabilities: is this student about to fail, and is this student about to stand
 * out. A single "probability score" would name neither.
 *
 * @param pFail P(RiesgoCritico), which is the probability of failing: that category holds the
 *     averages at or below 50, and the pass mark is 51. {@code EnRiesgo} passes narrowly and is
 *     deliberately not added in.
 * @param pOutstanding P(Sobresaliente).
 */
public record RiskScore(RiskLevel level, BigDecimal pFail, BigDecimal pOutstanding) {

    public RiskScore {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pFail, "pFail");
        Objects.requireNonNull(pOutstanding, "pOutstanding");
    }
}
