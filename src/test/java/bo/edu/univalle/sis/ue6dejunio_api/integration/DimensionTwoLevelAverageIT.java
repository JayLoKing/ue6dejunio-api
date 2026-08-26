package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.DimensionAvg;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dimension score averages CRITERIA, not raw scores. Each criterion collapses first — a
 * directly scored one keeps its single score, an activity-based one averages its items — and only
 * then do the criteria average into their dimension.
 *
 * <p>This is the rule the teacher stated, and it is not the same number as a flat average: with a
 * criterion holding four items next to one scored directly, a single-pass average lets the activity
 * outweigh the direct criterion four to one.
 */
class DimensionTwoLevelAverageIT extends AbstractIntegrationTest {

    @Autowired private IAssessmentScoreDomain scoreDomain;

    private UUID classGroup;
    private UUID enrollment;

    @BeforeEach
    void seed() {
        UUID teacher = seedUser("Teacher", false);
        UUID course = seedCourse(teacher, "A");
        classGroup = seedClassGroup(course, teacher, "Matematicas");
        enrollment = seedEnrollment(seedStudent(), course);
    }

    @Test
    void dimensionAverages_weighsEveryCriterionEqually_regardlessOfItemCount() {
        // Doing (tope 40): un criterio directo en 35 y una actividad de 4 criterios que promedia 32.
        UUID direct = seedCriterion(classGroup, 1, "Doing", "Participacion en clase");
        seedCriterionScore(enrollment, direct, 35);

        UUID activity = seedActivityCriterion(
            classGroup, 1, "Doing", "Evaluacion de avances de cuaderno", "Revision de Cuadernos");
        seedEventScore(enrollment, seedEvent(activity, "Tema 1"), 40);
        seedEventScore(enrollment, seedEvent(activity, "Tema 2"), 30);
        seedEventScore(enrollment, seedEvent(activity, "Tema 3"), 20);
        seedEventScore(enrollment, seedEvent(activity, "Tema 4"), 38);

        // (35 + 32) / 2 = 33.5. Un promedio plano sobre las 5 notas daria 32.6.
        assertThat(doingAverage()).isEqualByComparingTo("33.5");
    }

    @Test
    void dimensionAverages_withThreeCriteria_averagesTheThreeCriterionScores() {
        UUID direct = seedCriterion(classGroup, 1, "Doing", "Participacion en clase");
        seedCriterionScore(enrollment, direct, 35);

        UUID notebooks = seedActivityCriterion(
            classGroup, 1, "Doing", "Evaluacion de avances de cuaderno", "Revision de Cuadernos");
        seedEventScore(enrollment, seedEvent(notebooks, "Tema 1"), 40);
        seedEventScore(enrollment, seedEvent(notebooks, "Tema 2"), 30);
        seedEventScore(enrollment, seedEvent(notebooks, "Tema 3"), 20);
        seedEventScore(enrollment, seedEvent(notebooks, "Tema 4"), 38);

        UUID reading = seedActivityCriterion(
            classGroup, 1, "Doing", "Evaluacion de Control de Lectura", "Control de Lectura");
        seedEventScore(enrollment, seedEvent(reading, "Lectura 1"), 36);
        seedEventScore(enrollment, seedEvent(reading, "Lectura 2"), 34);
        seedEventScore(enrollment, seedEvent(reading, "Lectura 3"), 38);

        // (35 + 32 + 36) / 3 = 34.33. Dos actividades distintas no se mezclan entre si.
        assertThat(doingAverage()).isEqualByComparingTo("34.33");
    }

    @Test
    void dimensionAverages_separatesDimensions() {
        UUID doing = seedCriterion(classGroup, 1, "Doing", "Participacion");
        seedCriterionScore(enrollment, doing, 40);
        UUID being = seedCriterion(classGroup, 1, "Being", "Puntualidad");
        seedCriterionScore(enrollment, being, 9);

        List<DimensionAvg> avgs = scoreDomain.dimensionAverages(enrollment, classGroup, 1);

        assertThat(avgOf(avgs, "Doing")).isEqualByComparingTo("40");
        assertThat(avgOf(avgs, "Being")).isEqualByComparingTo("9");
    }

    @Test
    void dimensionAverages_ignoresOtherTrimesters() {
        UUID first = seedCriterion(classGroup, 1, "Doing", "Trimestre uno");
        seedCriterionScore(enrollment, first, 40);
        UUID second = seedCriterion(classGroup, 2, "Doing", "Trimestre dos");
        seedCriterionScore(enrollment, second, 10);

        assertThat(doingAverage()).isEqualByComparingTo("40");
    }

    private BigDecimal doingAverage() {
        return avgOf(scoreDomain.dimensionAverages(enrollment, classGroup, 1), "Doing");
    }

    private BigDecimal avgOf(List<DimensionAvg> avgs, String dimension) {
        return avgs.stream()
            .filter(a -> dimension.equals(a.dimension()))
            .findFirst()
            .map(a -> a.avgScore().setScale(2, RoundingMode.HALF_UP))
            .orElseThrow(() -> new AssertionError("Sin promedio para la dimension " + dimension));
    }
}
