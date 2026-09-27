package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One GROUP BY row of the per-student report: how many daily records of a given {@code status} one
 * enrolment holds on {@code date}.
 *
 * <p>The sibling of {@link DailyStatusCount}, which groups the same rows by date alone and
 * therefore knows a classroom's attendance without knowing anyone's in it. The date travels even
 * though the report totals by student, because which dates count is decided against the Director's
 * configured trimester periods — the caller cannot filter what it cannot see.
 *
 * <p>Numeric conversion (COUNT() arriving as Long) happens in the adapter, never here.
 */
public record EnrollmentStatusCount(
        UUID courseEnrollmentId, LocalDate date, String status, long count) {}
