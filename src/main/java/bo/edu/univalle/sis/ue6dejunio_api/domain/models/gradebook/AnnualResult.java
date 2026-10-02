package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;

/**
 * A student's academic result for the year.
 *
 * <p>Decided on the mark the libreta prints ({@link PrintedMark}) against the minimum in {@link
 * PassingMark}: 50.50 prints 51 and passes; 50.49 prints 50 and fails. There is no per-subject
 * rule. The per-trimester rule, {@link PassingMark#reachedBy}, is untouched: it compares the
 * unrounded average.
 */
public enum AnnualResult {
    APPROVED,
    FAILED;

    /**
     * The result for this final average, or null when there is no average: a student with nothing
     * graded was not judged, so they did not fail.
     */
    public static AnnualResult of(BigDecimal finalAverage) {
        if (finalAverage == null) {
            return null;
        }
        return PassingMark.reachedBy(PrintedMark.of(finalAverage)) ? APPROVED : FAILED;
    }
}
