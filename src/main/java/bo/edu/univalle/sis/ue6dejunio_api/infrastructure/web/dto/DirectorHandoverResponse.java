package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.DirectorHandoverDetail;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import java.time.LocalDateTime;
import java.util.UUID;

/** A handover as a screen shows it. Never carries a password, a hash or a token. */
public record DirectorHandoverResponse(
        UUID id,
        Party outgoing,
        IncomingParty incoming,
        boolean incomingCreated,
        LocalDateTime startedAt) {

    public record Party(UUID id, String fullName, String email) {}

    public record IncomingParty(
            UUID id, String fullName, String email, boolean mustChangePassword) {}

    public static DirectorHandoverResponse from(DirectorHandoverDetail d) {
        User out = d.outgoing();
        User in = d.incoming();
        return new DirectorHandoverResponse(
                d.id(),
                new Party(out.getId(), out.fullName(), out.getEmail()),
                new IncomingParty(
                        in.getId(), in.fullName(), in.getEmail(), in.isMustChangePassword()),
                d.incomingCreated(),
                d.startedAt());
    }
}
