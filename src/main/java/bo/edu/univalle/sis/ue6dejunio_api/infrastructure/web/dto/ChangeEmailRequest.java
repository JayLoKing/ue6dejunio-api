package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The own-email form. The address is validated as on user create. */
public record ChangeEmailRequest(
        @NotBlank @Email @Size(max = 100) String newEmail, @NotBlank String currentPassword) {}
