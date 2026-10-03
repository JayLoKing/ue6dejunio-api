package bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth;

import java.util.UUID;

/** What a user may change about themselves: the id comes from their token, never from the body. */
public record UpdateProfileCommand(UUID userId, String names, String lastNames, String phone) {}
