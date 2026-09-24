package bo.edu.univalle.sis.ue6dejunio_api.domain.models.user;

import java.util.UUID;

public record UsersList(
        UUID id,
        String ci,
        String names,
        String lastNames,
        String phone,
        String email,
        String role,
        boolean active) {}
