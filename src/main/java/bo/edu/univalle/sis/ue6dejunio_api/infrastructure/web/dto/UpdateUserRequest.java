package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @Size(max = 100) String names,
    @Size(max = 100) String lastNames,
    @Size(max = 20) @Pattern(regexp = "^[-0-9+ ]*$") String phone,
    @Positive Integer roleId,
    Boolean active
) {}
