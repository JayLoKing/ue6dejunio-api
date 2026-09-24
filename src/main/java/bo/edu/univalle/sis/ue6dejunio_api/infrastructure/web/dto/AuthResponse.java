package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        UUID userId,
        String email,
        String fullName,
        String role,
        String accessToken,
        String tokenType,
        Instant expiresAt,
        boolean mustChangePassword,
        String gradeName,
        String parallelName,
        UUID courseId,
        Boolean technical) {
    public static AuthResponse from(AuthenticatedUser u) {
        return new AuthResponse(
                u.userId(),
                u.email(),
                u.fullName(),
                u.role(),
                u.accessToken(),
                "Bearer",
                u.expiresAt(),
                u.mustChangePassword(),
                u.gradeName(),
                u.parallelName(),
                u.courseId(),
                u.technical());
    }
}
