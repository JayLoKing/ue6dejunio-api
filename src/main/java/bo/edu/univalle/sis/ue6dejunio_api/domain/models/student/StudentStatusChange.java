package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.UUID;

/**
 * What a student's status becomes, and everything that has to be written down with it.
 *
 * <p>One value rather than five parameters, because these travel together or not at all: a status
 * written without who decided it is exactly the row this record exists to stop.
 *
 * @param status what the student now is
 * @param reason the category behind it — one of {@link StudentWithdrawalReason}'s labels
 * @param note the Director's own words. Present when the category is the open one, which says
 *     nothing on its own; {@code null} otherwise
 * @param changedBy who decided. {@code null} only if the account is later removed, which the schema
 *     allows so a deleted user cannot take a student's record with them
 */
public record StudentStatusChange(String status, String reason, String note, UUID changedBy) {}
