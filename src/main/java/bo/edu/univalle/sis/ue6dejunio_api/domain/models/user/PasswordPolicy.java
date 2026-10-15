package bo.edu.univalle.sis.ue6dejunio_api.domain.models.user;

/**
 * The one rule for a password a person chooses for themselves.
 *
 * <p>At least eight characters, with an uppercase letter, a lowercase letter, a digit and a
 * character that is neither. The web shows the same five requirements as a checklist under the
 * field; the API enforces them because the web is not the only thing that can post a body.
 *
 * <p>Compile-time constants, so a request DTO can name them inside {@code @Pattern} and every form
 * that sets a password reads the rule from here rather than from a copy that drifts. The temporary
 * password the school generates meets it too, so the first change is never asked for something the
 * account it replaces did not have.
 *
 * <p>Negated classes rather than {@code .*} in the lookaheads, so a line break counts as the
 * special character it is in the web's check instead of silently ending the match.
 */
public final class PasswordPolicy {

    public static final String REGEX =
            "^(?=[^A-Z]*[A-Z])(?=[^a-z]*[a-z])(?=[^0-9]*[0-9])(?=[A-Za-z0-9]*[^A-Za-z0-9])"
                    + "[\\s\\S]{8,}$";

    public static final String MESSAGE =
            "La contraseña debe tener al menos 8 caracteres, con una mayúscula, una minúscula,"
                    + " un número y un carácter especial";

    private PasswordPolicy() {}
}
