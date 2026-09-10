package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import java.util.Arrays;
import java.util.Optional;

/**
 * What the model says is coming for a student in one subject.
 *
 * <p>The four categories are the ones the model was trained on, and {@link #modelName()} is how it
 * spells them on the wire. They are kept verbatim rather than translated: a mapping table between
 * the model's vocabulary and the school's would be a second place for these four words to live, and
 * retraining the model with a fifth category would silently fall through it.
 *
 * <p>Declared worst first. The order is documentation rather than behaviour: the severity ranking a
 * listing needs lives in the SQL {@code CASE} that sorts it, because the column holds these four
 * words and they do not sort into severity alphabetically. Whether a student is worth interrupting
 * a teacher over is {@link #demandsAttention}, not a comparison.
 */
public enum RiskLevel {

    /** Failing. The averages at or below 50, when the pass mark is 51. */
    RIESGO_CRITICO("RiesgoCritico"),

    /** 51 to 66: passing by a hair. Worth reading, not worth interrupting anybody over. */
    EN_RIESGO("EnRiesgo"),

    SIN_RIESGO("SinRiesgo"),

    SOBRESALIENTE("Sobresaliente");

    private final String modelName;

    RiskLevel(String modelName) {
        this.modelName = modelName;
    }

    /** How the model spells this category, and therefore how the column stores it. */
    public String modelName() {
        return modelName;
    }

    /**
     * Case-sensitive on purpose. A category the model did not answer is not a category whose
     * capitalisation drifted — it is a model this code has not been taught, and guessing at it
     * would store a level nobody trained.
     *
     * @return empty when the name is null or is not one of the four
     */
    public static Optional<RiskLevel> fromModel(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(l -> l.modelName.equals(name)).findFirst();
    }

    /**
     * Whether reaching this level is worth writing to a teacher about.
     *
     * <p>Only the failing one. Announcing {@code EnRiesgo} would announce most of the school —
     * it is the largest category in the training data — and an inbox that fills with it is an
     * inbox nobody opens on the day the real one arrives.
     */
    public boolean demandsAttention() {
        return this == RIESGO_CRITICO;
    }
}
