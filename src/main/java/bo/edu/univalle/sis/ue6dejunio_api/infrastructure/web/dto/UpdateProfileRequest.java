package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The own-profile form. Deliberately lacks CI, email, role and active: Jackson ignores unknown
 * properties, so sending them changes nothing. The email does change, but through {@code POST
 * /api/auth/change-email}, which asks for the current password first: the address is what login and
 * password recovery find the account by.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String names,
        @NotBlank @Size(max = 100) String lastNames,
        @Size(max = 20) @Pattern(regexp = "^[-0-9+ ]*$") String phone) {}
