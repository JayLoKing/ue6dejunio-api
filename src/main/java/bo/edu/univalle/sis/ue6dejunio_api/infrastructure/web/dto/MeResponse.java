package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import java.util.UUID;

public record MeResponse(
    UUID userId,
    String email,
    String fullName,
    String role,
    boolean mustChangePassword,
    String gradeName,
    String parallelName
) {}
