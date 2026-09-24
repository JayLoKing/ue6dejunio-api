package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RiskLevelTest {

    @Test
    void fromModel_everyCategoryTheModelAnswers_maps() {
        assertThat(RiskLevel.fromModel("RiesgoCritico")).contains(RiskLevel.RIESGO_CRITICO);
        assertThat(RiskLevel.fromModel("EnRiesgo")).contains(RiskLevel.EN_RIESGO);
        assertThat(RiskLevel.fromModel("SinRiesgo")).contains(RiskLevel.SIN_RIESGO);
        assertThat(RiskLevel.fromModel("Sobresaliente")).contains(RiskLevel.SOBRESALIENTE);
    }

    @Test
    void fromModel_unknownCategory_empty() {
        assertThat(RiskLevel.fromModel("Regular")).isEmpty();
    }

    @Test
    void fromModel_null_empty() {
        assertThat(RiskLevel.fromModel(null)).isEmpty();
    }

    @Test
    void fromModel_isCaseSensitive_becauseTheModelSpellsItOneWay() {
        assertThat(RiskLevel.fromModel("riesgocritico")).isEmpty();
    }

    @Test
    void modelName_roundTripsBackToWhatTheModelAnswered() {
        for (RiskLevel level : RiskLevel.values()) {
            assertThat(RiskLevel.fromModel(level.modelName())).contains(level);
        }
    }

    /**
     * Declared worst first. Nothing reads the order at runtime — the severity ranking lives in the
     * SQL that sorts a listing — but the declaration is what a reader goes by, and a category
     * inserted in the wrong place here would quietly disagree with that query.
     */
    @Test
    void theCategoriesAreDeclaredFromFailingToOutstanding() {
        assertThat(RiskLevel.values())
                .containsExactly(
                        RiskLevel.RIESGO_CRITICO,
                        RiskLevel.EN_RIESGO,
                        RiskLevel.SIN_RIESGO,
                        RiskLevel.SOBRESALIENTE);
    }

    /**
     * Only the failing category calls anyone. EnRiesgo passes, by a hair, and a message about every
     * student who passes narrowly is a message the school stops reading.
     */
    @Test
    void demandsAttention_onlyTheFailingCategory() {
        assertThat(RiskLevel.RIESGO_CRITICO.demandsAttention()).isTrue();
        assertThat(RiskLevel.EN_RIESGO.demandsAttention()).isFalse();
        assertThat(RiskLevel.SIN_RIESGO.demandsAttention()).isFalse();
        assertThat(RiskLevel.SOBRESALIENTE.demandsAttention()).isFalse();
    }
}
