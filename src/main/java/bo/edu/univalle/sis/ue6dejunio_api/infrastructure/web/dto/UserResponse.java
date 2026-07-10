package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String ci,
    String names,
    String lastNames,
    String phone,
    String email,
    String role,
    boolean technical,
    boolean active,
    boolean mustChangePassword,
    LocalDateTime createdAt
) {
    public static UserResponse from(User u) {
        return new UserResponse(
            u.getId(), u.getCi(), u.getNames(), u.getLastNames(),
            u.getPhone(), u.getEmail(),
            u.getRole() != null ? u.getRole().name() : null,
            u.isTechnical(), u.isActive(), u.isMustChangePassword(), u.getCreatedAt());
    }
}
