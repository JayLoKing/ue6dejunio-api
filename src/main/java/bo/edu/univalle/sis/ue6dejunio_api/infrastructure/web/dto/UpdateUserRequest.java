package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The Director's edit form. Every field is optional. {@code email} and {@code ci} carry the same
 * validation as on create, so a value that could not be registered cannot be edited in either.
 * Optional here, so the {@code @NotBlank} of create becomes the service refusing a blank one.
 *
 * <p>No {@code active}: the account is closed and reopened through {@code DELETE /{id}} and {@code
 * POST /{id}/activate}. A body that still sends the flag has it ignored, not applied.
 */
public record UpdateUserRequest(
        @Size(max = 100) String names,
        @Size(max = 100) String lastNames,
        @Size(max = 20) @Pattern(regexp = "^[-0-9+ ]*$") String phone,
        @Positive Integer roleId,
        @Email @Size(max = 100) String email,
        @Size(min = 5, max = 15) String ci) {}
