package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The email is normalized before Bean Validation runs (see {@link LoginRequest}). */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 100) String email) {

    public ForgotPasswordRequest {
        email = Emails.normalize(email);
    }
}
