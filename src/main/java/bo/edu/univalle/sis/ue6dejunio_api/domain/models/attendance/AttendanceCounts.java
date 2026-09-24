package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Attendance counts for a scope (overall / month / trimester).
 *
 * <p>Percentage rule: percentage = present * 100 / (present + absent + late), rounded HALF_UP to 1
 * decimal (0-100 scale). Excused is excluded from both numerator and denominator. Late counts as a
 * miss (denominator yes, numerator no). If computableSessions == 0, percentage is null (never 0,
 * never NaN).
 */
public record AttendanceCounts(
        long present,
        long absent,
        long late,
        long excused,
        long computableSessions,
        BigDecimal percentage) {
    public static AttendanceCounts of(long present, long absent, long late, long excused) {
        long computableSessions = present + absent + late;
        BigDecimal percentage =
                computableSessions == 0
                        ? null
                        : BigDecimal.valueOf(present)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(
                                        BigDecimal.valueOf(computableSessions),
                                        1,
                                        RoundingMode.HALF_UP);
        return new AttendanceCounts(present, absent, late, excused, computableSessions, percentage);
    }
}
