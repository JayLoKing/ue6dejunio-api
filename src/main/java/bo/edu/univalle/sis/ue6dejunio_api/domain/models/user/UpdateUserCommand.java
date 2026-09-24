package bo.edu.univalle.sis.ue6dejunio_api.domain.models.user;

public record UpdateUserCommand(
        String names, String lastNames, String phone, Integer roleId, Boolean active) {}
