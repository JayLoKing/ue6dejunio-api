package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The own-profile form. Deliberately lacks CI, email, role and active: Jackson ignores unknown
 * properties, so sending them changes nothing.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String names,
        @NotBlank @Size(max = 100) String lastNames,
        @Size(max = 20) @Pattern(regexp = "^[-0-9+ ]*$") String phone) {}
