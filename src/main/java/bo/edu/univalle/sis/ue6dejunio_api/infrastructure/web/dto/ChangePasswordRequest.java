package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 8, max = 100) String newPassword
) {}
