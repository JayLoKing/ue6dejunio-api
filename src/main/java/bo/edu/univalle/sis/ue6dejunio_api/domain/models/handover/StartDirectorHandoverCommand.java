package bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover;

import java.util.UUID;

/**
 * What the outgoing Director sends to open a handover. The email always identifies the incoming
 * Director. {@code ci}, {@code names} and {@code lastNames} are read only when no user has that
 * email yet and the handover has to create the account; for an existing user they are ignored.
 */
public record StartDirectorHandoverCommand(
        UUID outgoingId, String email, String ci, String names, String lastNames, String phone) {}
