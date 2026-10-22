package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Attendance counts for a scope (overall / month / trimester).
 *
 * <p>Percentage rule (set by the school on 2026-10-22): percentage = (present + late) * 100 /
 * (present + absent + late + excused), rounded HALF_UP to 1 decimal (0-100 scale). Late counts as
 * attended, like Present. Absent and Excused (licencia) both count as a falta: denominator yes,
 * numerator no. If computableSessions == 0, percentage is null (never 0, never NaN).
 *
 * <p>{@code computableSessions} keeps its name because it is part of the API responses; it is the
 * number of marked sessions of any status.
 */
public record AttendanceCounts(
        long present,
        long absent,
        long late,
        long excused,
        long computableSessions,
        BigDecimal percentage) {
    public static AttendanceCounts of(long present, long absent, long late, long excused) {
        long computableSessions = present + absent + late + excused;
        long attended = present + late;
        BigDecimal percentage =
                computableSessions == 0
                        ? null
                        : BigDecimal.valueOf(attended)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(
                                        BigDecimal.valueOf(computableSessions),
                                        1,
                                        RoundingMode.HALF_UP);
        return new AttendanceCounts(present, absent, late, excused, computableSessions, percentage);
    }
}
