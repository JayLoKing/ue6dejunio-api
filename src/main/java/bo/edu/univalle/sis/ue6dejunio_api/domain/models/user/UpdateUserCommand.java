package bo.edu.univalle.sis.ue6dejunio_api.domain.models.user;

/**
 * Every field is optional: null leaves it as it is.
 *
 * <p>No {@code active}: closing and reopening an account go through their own use cases, and an
 * edit that also carried the flag was a second way to do it that skipped them.
 */
public record UpdateUserCommand(
        String names, String lastNames, String phone, Integer roleId, String email, String ci) {}
