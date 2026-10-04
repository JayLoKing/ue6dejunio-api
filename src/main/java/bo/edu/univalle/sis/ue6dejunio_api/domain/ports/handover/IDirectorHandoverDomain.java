package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.handover;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.DirectorHandover;
import java.util.Optional;
import java.util.UUID;

/** Persistence of the Director handover row. Rows are never deleted: the history stays. */
public interface IDirectorHandoverDomain {

    /** The handover in progress, if any. There is at most one. */
    Optional<DirectorHandover> findPending();

    /** The pending handover whose incoming Director is this user, if there is one. */
    Optional<DirectorHandover> findPendingByIncoming(UUID incomingId);

    /**
     * Inserts or updates the row and flushes it.
     *
     * @throws bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException when it would
     *     be a second pending row; the database index refuses that even if two requests race past
     *     the service's own check
     */
    DirectorHandover save(DirectorHandover handover);
}
