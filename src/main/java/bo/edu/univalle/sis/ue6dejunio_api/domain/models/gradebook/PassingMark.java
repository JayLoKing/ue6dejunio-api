package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;

/**
 * The mark a student has to reach to pass an area.
 *
 * <p>Stated once, here, because the school's documents disagree about everything except this: the
 * libreta counts areas passed and failed by it, the centralizer prints a situation by it, and the
 * risk model's RiesgoCritico category is defined as the averages that fall below it. A second copy
 * of the number is a second chance for one document to call a student passed while another calls
 * them failed.
 */
public final class PassingMark {

    /** 51 out of 100. A mark of exactly 51 passes; 50 does not. */
    public static final BigDecimal MINIMUM = new BigDecimal("51");

    private PassingMark() {}

    /** Whether this mark reaches the minimum. An absent mark is neither passed nor failed. */
    public static boolean reachedBy(BigDecimal mark) {
        return mark != null && mark.compareTo(MINIMUM) >= 0;
    }
}
