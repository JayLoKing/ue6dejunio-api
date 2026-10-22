package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The own-email form. The address is validated as on user create, and normalized before that
 * validation runs (see {@link LoginRequest}).
 */
public record ChangeEmailRequest(
        @NotBlank @Email @Size(max = 100) String newEmail, @NotBlank String currentPassword) {

    public ChangeEmailRequest {
        newEmail = Emails.normalize(newEmail);
    }
}
