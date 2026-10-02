package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The mark as the libreta prints it: the average taken to a whole number, HALF_UP.
 *
 * <p>It lives in one place because two things must always agree: the numeral and the words on the
 * libreta ({@link GradeInWords}) and the annual result frozen when a year closes ({@link
 * AnnualResult}). If each rounded on its own, a 50.5 could print as 51 and be stored as failed.
 */
public final class PrintedMark {

    private PrintedMark() {}

    /** The whole mark that gets printed, or null when there is no mark. */
    public static BigDecimal of(BigDecimal mark) {
        return mark == null ? null : mark.setScale(0, RoundingMode.HALF_UP);
    }
}
