package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The email names the incoming Director. {@code ci}, {@code names} and {@code lastNames} are needed
 * only when no user has that email yet, so they cannot be {@code @NotBlank} here: the service
 * demands them once it knows whether an account has to be created. Their size limits are the same
 * as {@link CreateUserRequest}'s.
 */
public record StartDirectorHandoverRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @Size(min = 5, max = 15) String ci,
        @Size(max = 100) String names,
        @Size(max = 100) String lastNames,
        @Size(max = 20) @Pattern(regexp = "^[-0-9+ ]*$") String phone) {}
