package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;

/**
 * What the own-profile form shows and edits. The token carries only a joined full name, which
 * cannot be split back into names and last names, and a Teacher cannot read their own row through
 * the Director-only user endpoints.
 */
public record ProfileResponse(
        String ci, String names, String lastNames, String phone, String email) {
    public static ProfileResponse from(User u) {
        return new ProfileResponse(
                u.getCi(), u.getNames(), u.getLastNames(), u.getPhone(), u.getEmail());
    }
}
