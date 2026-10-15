package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Both the first-login change of the temporary password and the change from the menu post this, so
 * the rule in {@link PasswordPolicy} covers them in one place. The minimum length lives in the
 * pattern, so a short password reads one message describing the whole rule instead of two.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
                @Size(max = 100)
                @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
                String newPassword) {}
