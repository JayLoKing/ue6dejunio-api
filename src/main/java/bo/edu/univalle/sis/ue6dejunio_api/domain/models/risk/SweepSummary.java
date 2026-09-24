package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

/**
 * What one drain of the queue did.
 *
 * <p>Logged rather than returned to anybody: nothing is waiting on a sweep. It exists so the line
 * in the log says what happened instead of that something happened.
 *
 * @param subjects how many queued subjects were taken. Zero is the ordinary case — most ticks find
 *     nothing, and a sweep that finds nothing must stay silent rather than write a line every few
 *     minutes forever.
 * @param considered student-subject vectors assembled
 * @param skipped of those, the ones the model cannot be asked about yet: a dimension with no mark,
 *     or a subject with nothing planned
 * @param predicted vectors actually sent and scored
 * @param transitions predictions whose risk level changed, which is what becomes a notification
 */
public record SweepSummary(
        int subjects, int considered, int skipped, int predicted, int transitions) {

    public static SweepSummary empty() {
        return new SweepSummary(0, 0, 0, 0, 0);
    }

    /** Whether this sweep had anything to do at all. */
    public boolean isEmpty() {
        return subjects == 0;
    }
}
