package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UsersList;
import java.util.UUID;

/**
 * One row of the user directory. Its own type, so renaming a field in the domain model is not an
 * API break for whoever is listing users.
 */
public record UserListResponse(
        UUID id,
        String ci,
        String names,
        String lastNames,
        String phone,
        String email,
        String role,
        boolean active) {
    public static UserListResponse from(UsersList u) {
        return new UserListResponse(
                u.id(),
                u.ci(),
                u.names(),
                u.lastNames(),
                u.phone(),
                u.email(),
                u.role(),
                u.active());
    }
}
