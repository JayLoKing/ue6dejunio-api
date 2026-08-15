package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import java.time.LocalDate;

/**
 * One GROUP BY row: how many daily attendance records of a given {@code status} fall on
 * {@code date} for a course. Produced by a single aggregated query (no per-enrollment loop).
 * Numeric/driver-specific conversion (e.g. COUNT() returning Long) happens in the
 * infrastructure adapter, never here — this record stays free of JDBC concerns.
 */
public record DailyStatusCount(LocalDate date, String status, long count) {}
