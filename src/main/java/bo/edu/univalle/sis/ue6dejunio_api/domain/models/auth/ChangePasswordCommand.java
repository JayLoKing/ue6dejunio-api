package bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth;

import java.util.UUID;

public record ChangePasswordCommand(
    UUID userId,
    String currentPassword,
    String newPassword
) {}
