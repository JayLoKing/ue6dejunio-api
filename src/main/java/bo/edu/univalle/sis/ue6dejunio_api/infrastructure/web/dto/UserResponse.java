package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UserUpdate;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A user as the Director's screens read it.
 *
 * <p>{@code credentialsResent} is only present in the answer to an edit, where it says whether
 * correcting the email also mailed a new temporary password to it. Everywhere else it is left out.
 */
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
        LocalDateTime createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean credentialsResent) {
    public static UserResponse from(User u) {
        return of(u, null);
    }

    public static UserResponse from(UserUpdate update) {
        return of(update.user(), update.credentialsResent());
    }

    private static UserResponse of(User u, Boolean credentialsResent) {
        return new UserResponse(
                u.getId(),
                u.getCi(),
                u.getNames(),
                u.getLastNames(),
                u.getPhone(),
                u.getEmail(),
                u.getRole() != null ? u.getRole().name() : null,
                u.isTechnical(),
                u.isActive(),
                u.isMustChangePassword(),
                u.getCreatedAt(),
                credentialsResent);
    }
}
