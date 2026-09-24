package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(
        UUID userId,
        String email,
        String fullName,
        String role,
        boolean mustChangePassword,
        String gradeName,
        String parallelName,
        UUID courseId,
        Boolean technical) {}
