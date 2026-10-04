package bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import java.time.LocalDateTime;
import java.util.UUID;

/** A pending handover with both accounts loaded, which is what a screen needs to describe it. */
public record DirectorHandoverDetail(
        UUID id, User outgoing, User incoming, boolean incomingCreated, LocalDateTime startedAt) {}
