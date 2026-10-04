package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import java.util.UUID;

/** Just enough to say who an email belongs to; nothing the Director could not already list. */
public record UserLookupResponse(
        UUID id, String fullName, String email, String role, boolean active) {
    public static UserLookupResponse from(User u) {
        return new UserLookupResponse(
                u.getId(),
                u.fullName(),
                u.getEmail(),
                u.getRole() != null ? u.getRole().name() : null,
                u.isActive());
    }
}
