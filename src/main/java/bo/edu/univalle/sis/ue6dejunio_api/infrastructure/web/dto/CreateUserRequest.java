package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** The email is normalized before Bean Validation runs (see {@link LoginRequest}). */
public record CreateUserRequest(
        @NotBlank @Size(min = 5, max = 15) String ci,
        @NotBlank @Size(max = 100) String names,
        @NotBlank @Size(max = 100) String lastNames,
        @Size(max = 20) @Pattern(regexp = "^[-0-9+ ]*$") String phone,
        @NotBlank @Email @Size(max = 100) String email,
        @NotNull @Positive Integer roleId,
        Boolean technical) {

    public CreateUserRequest {
        email = Emails.normalize(email);
    }
}
