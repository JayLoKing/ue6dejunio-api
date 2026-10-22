package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The email is normalized on construction, before Bean Validation runs: {@code @Email} rejects
 * surrounding whitespace, so a space pasted along with the address would otherwise end in a 400
 * instead of a login. The password is left exactly as typed; a space can be part of it.
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 6, max = 100) String password) {

    public LoginRequest {
        email = Emails.normalize(email);
    }
}
