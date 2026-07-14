package bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth;

import java.time.Instant;
import java.util.UUID;

public record AuthenticatedUser(
    UUID userId,
    String email,
    String fullName,
    String role,
    String accessToken,
    Instant issuedAt,
    Instant expiresAt,
    boolean mustChangePassword,
    String gradeName,
    String parallelName,
    UUID courseId,
    Boolean technical
) {}
