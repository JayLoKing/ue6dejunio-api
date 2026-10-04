package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.handover;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.DirectorHandoverDetail;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.StartDirectorHandoverCommand;
import java.util.Optional;
import java.util.UUID;

/**
 * The handover of the Director role: the only door that mints a Director. The outgoing Director
 * names a successor and keeps full access; the outgoing account is closed only when the successor
 * has replaced the temporary password, so the school never ends up with nobody in charge.
 */
public interface IDirectorHandoverService {

    /**
     * Opens a handover: promotes the user with that email, or creates one when nobody has it, and
     * mails the temporary credentials. Refused (409) when a handover is already pending, when the
     * email is the caller's own or belongs to a Director or to a closed account, or (400) when a
     * new account is needed and its data is missing.
     */
    DirectorHandoverDetail start(StartDirectorHandoverCommand command);

    /** The handover in progress, if any. */
    Optional<DirectorHandoverDetail> findPending();

    /**
     * Calls the pending handover off. Only the outgoing Director may (403 otherwise). An account
     * the handover created is closed; a promoted user gets the previous role back and stays active.
     * The outgoing Director is never touched.
     */
    void cancel(UUID callerId);

    /**
     * Called whenever a user sets a password of their own. If that user is the incoming Director of
     * the pending handover, the handover closes and the outgoing account is deactivated, in the
     * caller's transaction.
     *
     * @return whether a handover was closed
     */
    boolean completeFor(UUID userId);
}
