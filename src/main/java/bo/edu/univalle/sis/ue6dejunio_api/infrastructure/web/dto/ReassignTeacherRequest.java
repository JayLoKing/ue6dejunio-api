package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReassignTeacherRequest(
    @NotNull UUID teacherId
) {}
