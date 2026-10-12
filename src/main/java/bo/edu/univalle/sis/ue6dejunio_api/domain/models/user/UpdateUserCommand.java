package bo.edu.univalle.sis.ue6dejunio_api.domain.models.user;

/** Every field is optional: null leaves it as it is. */
public record UpdateUserCommand(
        String names,
        String lastNames,
        String phone,
        Integer roleId,
        Boolean active,
        String email) {}
