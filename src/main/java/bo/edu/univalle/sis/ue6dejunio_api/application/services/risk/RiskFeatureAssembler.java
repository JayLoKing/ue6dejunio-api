package bo.edu.univalle.sis.ue6dejunio_api.application.services.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskFeatures;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain.AttendanceRateRow;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskFeatureDomain.CriterionScoreRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns rows into vectors.
 *
 * <p>Its own class, and no database anywhere near it, because everything it does is a rule about
 * the model rather than about storage: which dimension a mark belongs to, that the order of marks
 * within a dimension is the trend, that a subject with nothing planned has no progress to report.
 * Buried in a query, none of it could be tested without a database, and all of it would have to be
 * re-read to answer what the model is actually being told.
 */
class RiskFeatureAssembler {

    private static final Logger log = LoggerFactory.getLogger(RiskFeatureAssembler.class);

    private record Key(UUID studentId, UUID classGroupId) {}

    /**
     * @param plannedCriteria a subject absent from this map yields vectors carrying zero, which no
     *     vector can be predicted on: progress with nothing to divide by is not a figure, it is a
     *     question the data cannot answer yet. Zero rather than omission so those students are
     *     still counted as skipped instead of vanishing from the run summary altogether.
     */
    static List<RiskFeatures> assemble(
            int trimester,
            List<CriterionScoreRow> scores,
            Map<UUID, Integer> plannedCriteria,
            List<AttendanceRateRow> attendanceRates) {
        // Insertion-ordered so a run built from the same rows sends the same batch in the same
        // order. A HashMap here would reshuffle the vectors between two identical runs, which turns
        // the boundary between one chunked request and the next into a coin toss and makes a
        // failing run impossible to reproduce.
        Map<Key, Builder> builders = new LinkedHashMap<>();

        for (CriterionScoreRow score : scores) {
            Key key = new Key(score.studentId(), score.classGroupId());
            builders.computeIfAbsent(key, k -> new Builder(k.studentId(), k.classGroupId()))
                    .addScore(score.dimension(), score.score());
        }

        for (AttendanceRateRow rate : attendanceRates) {
            Key key = new Key(rate.studentId(), rate.classGroupId());
            Builder builder = builders.get(key);
            if (builder != null) {
                builder.attendancePct = rate.attendancePct();
            }
        }

        List<RiskFeatures> result = new ArrayList<>(builders.size());
        for (Builder b : builders.values()) {
            // A subject with nothing planned yields a vector carrying zero, not no vector at all.
            // Dropped here, its students would be missing from both halves of the run summary —
            // neither considered nor skipped — and a subject whose teacher entered marks without
            // planning criteria would report as though it did not exist. Zero is a denominator the
            // vector cannot use, which is what makes it incomplete, and incomplete is countable.
            Integer planned = plannedCriteria.get(b.classGroupId);
            result.add(b.build(trimester, planned == null ? 0 : planned));
        }
        return result;
    }

    private static class Builder {
        private final UUID studentId;
        private final UUID classGroupId;
        private final List<BigDecimal> being = new ArrayList<>();
        private final List<BigDecimal> knowing = new ArrayList<>();
        private final List<BigDecimal> doing = new ArrayList<>();
        private final List<BigDecimal> deciding = new ArrayList<>();
        private BigDecimal attendancePct;

        Builder(UUID studentId, UUID classGroupId) {
            this.studentId = studentId;
            this.classGroupId = classGroupId;
        }

        /**
         * The four the model was trained on. A fifth is dropped and said so: silently swallowing it
         * would shrink a dimension nobody notices is short, and guessing at a home for it would
         * feed the model a mark it never saw in training.
         */
        void addScore(String dimension, BigDecimal score) {
            switch (dimension) {
                case "Being" -> being.add(score);
                case "Knowing" -> knowing.add(score);
                case "Doing" -> doing.add(score);
                case "Deciding" -> deciding.add(score);
                default ->
                        log.warn(
                                "Criterion dimension '{}' is not one the model knows, so the mark is left out "
                                        + "of the vector for student {} in class group {}",
                                dimension,
                                studentId,
                                classGroupId);
            }
        }

        RiskFeatures build(int trimester, int planned) {
            return new RiskFeatures(
                    studentId,
                    classGroupId,
                    trimester,
                    being,
                    knowing,
                    doing,
                    deciding,
                    attendancePct,
                    planned);
        }
    }
}
