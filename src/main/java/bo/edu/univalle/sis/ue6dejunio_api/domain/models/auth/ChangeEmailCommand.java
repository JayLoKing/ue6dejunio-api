package bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth;

import java.util.UUID;

/**
 * A user moving their own account to another address. The id comes from their token; the current
 * password is what proves the person holding the session is its owner.
 */
public record ChangeEmailCommand(UUID userId, String newEmail, String currentPassword) {}
