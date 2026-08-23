package bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One attendance record. A daily (course-wide) record has a null {@code classGroupId}; a session
 * record carries the class group it belongs to.
 *
 * <p>The audit fields answer "who marked this, and who last changed it". Both write paths are
 * upserts, so {@code createdBy}/{@code createdAt} keep the first author while
 * {@code updatedBy}/{@code updatedAt} move with every correction.
 */
public record Attendance(
    UUID id,
    UUID courseEnrollmentId,
    UUID classGroupId,
    LocalDate date,
    String status,
    UUID createdBy,
    LocalDateTime createdAt,
    UUID updatedBy,
    LocalDateTime updatedAt
) {

    /** Audit-less projection, for callers that neither persist nor read the audit trail. */
    public Attendance(UUID id, UUID courseEnrollmentId, UUID classGroupId, LocalDate date, String status) {
        this(id, courseEnrollmentId, classGroupId, date, status, null, null, null, null);
    }
}
