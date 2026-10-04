package bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The handover of the Director role: one row from the day the outgoing Director names a successor
 * until the successor sets a password of their own (closed) or the outgoing Director calls it off
 * (cancelled).
 *
 * <p>Pending means neither has happened. The database allows one pending row at most, and a row is
 * never both closed and cancelled, so {@link #isPending()} is a complete description of the state.
 *
 * @param previousRoleId the role a promoted user held before; {@code null} for an account the
 *     handover created
 */
public record DirectorHandover(
        UUID id,
        UUID outgoingId,
        UUID incomingId,
        boolean incomingCreated,
        Integer previousRoleId,
        LocalDateTime startedAt,
        LocalDateTime closedAt,
        LocalDateTime cancelledAt) {

    public boolean isPending() {
        return closedAt == null && cancelledAt == null;
    }

    public DirectorHandover close(LocalDateTime at) {
        return new DirectorHandover(
                id, outgoingId, incomingId, incomingCreated, previousRoleId, startedAt, at, null);
    }

    public DirectorHandover cancel(LocalDateTime at) {
        return new DirectorHandover(
                id, outgoingId, incomingId, incomingCreated, previousRoleId, startedAt, null, at);
    }
}
