package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.UUID;

/**
 * Taking a student off the roll.
 *
 * @param studentId who is leaving
 * @param reason which of the school's categories it falls under
 * @param note the Director's own words. Required by {@link StudentWithdrawalReason#OTRO}, which
 *             exists for a reason the list does not have and therefore has to say what it was;
 *             optional beside the named ones, where it adds detail rather than the reason itself
 * @param actorId the Director making the decision, recorded so the teacher who reads the notice
 *                knows who to ask about it
 */
public record WithdrawStudentCommand(
    UUID studentId,
    StudentWithdrawalReason reason,
    String note,
    UUID actorId
) {
}
