package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskFeaturesTest {

    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID CLASS_GROUP = UUID.randomUUID();

    private static RiskFeatures of(List<BigDecimal> being, List<BigDecimal> knowing,
                                   List<BigDecimal> doing, List<BigDecimal> deciding) {
        return new RiskFeatures(STUDENT, CLASS_GROUP, 1, being, knowing, doing, deciding,
            new BigDecimal("87.5"), 12);
    }

    private static List<BigDecimal> scores(String... values) {
        return List.of(values).stream().map(BigDecimal::new).toList();
    }

    /**
     * The service refuses to send an incomplete vector, because the model answers 422 to it. The
     * check belongs here so the caller never has to know that rule as an HTTP status.
     */
    @Test
    void isComplete_everyDimensionScored_true() {
        assertThat(of(scores("3"), scores("10", "12"), scores("8"), scores("2")).isComplete())
            .isTrue();
    }

    @Test
    void isComplete_oneDimensionEmpty_false() {
        assertThat(of(scores("3"), scores("10"), List.of(), scores("2")).isComplete()).isFalse();
    }

    @Test
    void isComplete_nothingScored_false() {
        assertThat(of(List.of(), List.of(), List.of(), List.of()).isComplete()).isFalse();
    }

    /** Progress is marks over what was planned, and nothing planned is no denominator. */
    @Test
    void isComplete_nothingPlannedInTheSubject_false() {
        RiskFeatures features = new RiskFeatures(STUDENT, CLASS_GROUP, 1,
            scores("3"), scores("10"), scores("8"), scores("2"), new BigDecimal("87.5"), 0);

        assertThat(features.isComplete()).isFalse();
    }

    /**
     * Attendance is optional to the model, which predicts on the marks alone without it. Required
     * here, a single missing trimester period would make the whole school unpredictable.
     */
    @Test
    void isComplete_nobodyHasTakenARollYet_stillTrue() {
        RiskFeatures features = new RiskFeatures(STUDENT, CLASS_GROUP, 1,
            scores("3"), scores("10"), scores("8"), scores("2"), null, 12);

        assertThat(features.isComplete()).isTrue();
    }

    @Test
    void nullDimension_becomesEmpty_ratherThanBlowingUpAtTheHttpBoundary() {
        RiskFeatures features = of(null, scores("10"), scores("8"), scores("2"));

        assertThat(features.being()).isEmpty();
        assertThat(features.isComplete()).isFalse();
    }

    /**
     * The lists cross a boundary and end up serialized. A caller holding the original must not be
     * able to change what was sent after the fact.
     */
    @Test
    void dimensions_areCopied_soTheCallerCannotMutateThemAfterwards() {
        List<BigDecimal> mutable = new ArrayList<>(scores("10"));
        RiskFeatures features = of(scores("3"), mutable, scores("8"), scores("2"));

        mutable.add(new BigDecimal("45"));

        assertThat(features.knowing()).containsExactly(new BigDecimal("10"));
    }

    @Test
    void dimensions_areUnmodifiable() {
        RiskFeatures features = of(scores("3"), scores("10"), scores("8"), scores("2"));

        assertThatThrownBy(() -> features.knowing().add(BigDecimal.ONE))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void studentId_isRequired() {
        assertThatThrownBy(() -> new RiskFeatures(null, CLASS_GROUP, 1, List.of(), List.of(),
            List.of(), List.of(), BigDecimal.ZERO, 0))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void classGroupId_isRequired() {
        assertThatThrownBy(() -> new RiskFeatures(STUDENT, null, 1, List.of(), List.of(),
            List.of(), List.of(), BigDecimal.ZERO, 0))
            .isInstanceOf(NullPointerException.class);
    }
}
