package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Recovery sets a password the person chooses, so it is held to {@link PasswordPolicy} too. */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank
                @Size(max = 100)
                @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
                String newPassword) {}
