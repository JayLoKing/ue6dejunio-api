package bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A Director-configured date range for one trimester of one academic year. Attendance rows
 * carry no trimester column; this is the single source of truth for mapping a date to a
 * trimester (replaces the previous hardcoded AcademicTrimesterCalendar month map).
 */
public record TrimesterPeriod(
    UUID id,
    Integer academicYearId,
    int trimester,
    LocalDate startDate,
    LocalDate endDate
) {
    /** Whether {@code date} falls within [startDate, endDate], inclusive on both ends. */
    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /** Whether [otherStart, otherEnd] shares at least one day with this period's range. */
    public boolean overlaps(LocalDate otherStart, LocalDate otherEnd) {
        return !otherStart.isAfter(endDate) && !startDate.isAfter(otherEnd);
    }
}
