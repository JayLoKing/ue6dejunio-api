package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything the model is given about one student in one subject for one trimester.
 *
 * <p>The four dimension lists are the per-criterion marks <b>ordered by the date they were
 * entered</b>, not aggregates. The model derives its own mean, spread and trend from them, and the
 * trend is literally last minus first — so the order is part of the value, and a set would lose it.
 *
 * <p>Each list is in the scale of its own dimension, the same one the gradebook uses: Being out of
 * 10, Knowing out of 45, Doing out of 40, Deciding out of 5.
 *
 * @param attendancePct attendance so far, not attendance at the close of the trimester. Waiting for
 *     the trimester to end produces a warning about a student whose marks are already final.
 * @param plannedCriteria how many criteria the teacher planned for this subject and trimester. It
 *     is the denominator of progress: three marks out of three and three out of seven are the same
 *     count and mean opposite things.
 */
public record RiskFeatures(
        UUID studentId,
        UUID classGroupId,
        int trimester,
        List<BigDecimal> being,
        List<BigDecimal> knowing,
        List<BigDecimal> doing,
        List<BigDecimal> deciding,
        BigDecimal attendancePct,
        int plannedCriteria) {

    public RiskFeatures {
        Objects.requireNonNull(studentId, "studentId");
        Objects.requireNonNull(classGroupId, "classGroupId");
        // Copied, not referenced: these lists are assembled from a query result the caller still
        // holds, and they end up serialized. A vector that can change after it was built is a
        // prediction whose stored inputs no longer explain it.
        being = copyOf(being);
        knowing = copyOf(knowing);
        doing = copyOf(doing);
        deciding = copyOf(deciding);
    }

    private static List<BigDecimal> copyOf(List<BigDecimal> scores) {
        return scores == null ? List.of() : List.copyOf(scores);
    }

    /**
     * Whether this student can be predicted at all yet.
     *
     * <p>The model needs at least one mark in each of the four dimensions and answers 422 without
     * them. Asked here so a student halfway through their first trimester is skipped quietly,
     * rather than turned into a failed HTTP call the scheduler has to interpret — and a rejected
     * batch costs every vector in it, not just the one that was short.
     *
     * <p>A planned count of zero fails this too. Progress is marks over what was planned, and with
     * nothing planned there is no denominator: the answer would be a statement about a subject
     * whose shape nobody has declared yet.
     *
     * <p>Attendance is deliberately not part of this. The model declares it optional and predicts
     * without it, so a student in a subject nobody has taken a roll for yet is predicted on their
     * marks alone. Requiring it here would make a missing trimester period — one configuration row
     * — silently unpredictable for the whole school.
     */
    public boolean isComplete() {
        return plannedCriteria > 0
                && !being.isEmpty()
                && !knowing.isEmpty()
                && !doing.isEmpty()
                && !deciding.isEmpty();
    }
}
