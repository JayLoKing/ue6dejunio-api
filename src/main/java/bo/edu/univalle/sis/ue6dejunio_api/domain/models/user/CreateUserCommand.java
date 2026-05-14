package bo.edu.univalle.sis.ue6dejunio_api.domain.models.user;

public record CreateUserCommand(
    String ci,
    String names,
    String lastNames,
    String phone,
    String email,
    Integer roleId
) {}
