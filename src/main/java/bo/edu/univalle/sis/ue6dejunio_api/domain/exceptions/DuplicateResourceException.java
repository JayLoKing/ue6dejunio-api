package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

import java.util.Map;

/**
 * Something the school already has on record.
 *
 * <p>The message is written for the person who hit it. The web prints it verbatim in a toast, so
 * {@code Recurso duplicado: email=ana@ue6.bo} — a field name, an equals sign and a value — handed
 * the Director creating an account a log line to translate. The field stays as the caller's key,
 * because it is also what an operator greps for; only the wording that reaches a screen changes.
 */
public class DuplicateResourceException extends DomainException {

    /**
     * How the school names each field it can collide on.
     *
     * <p>Only the ones a person meets in a form. The rest are composite keys that callers already
     * write as descriptions, and the fallback frames those.
     *
     * <p>Each one carries the value back. The Director creating an account in bulk is not always
     * looking at the field they typed it into, and naming the address is what lets them tell which
     * of the two they pasted is the one already on record.
     */
    private static final Map<String, String> SENTENCES =
            Map.of(
                    "email", "El correo %s ya está registrado con un usuario del sistema.",
                    "ci", "El CI %s ya está registrado con un usuario del sistema.");

    public DuplicateResourceException(String field, String value) {
        super(sentenceFor(field, value));
    }

    private static String sentenceFor(String field, String value) {
        String known = SENTENCES.get(field);
        if (known != null) {
            return known.formatted(value);
        }
        if ("name".equals(field) || "grade name".equals(field)) {
            return "Ya existe un registro con el nombre \"%s\".".formatted(value);
        }
        // A composite key — "curso (grado+paralelo+anio)" and the like. The value is kept because
        // for a composite it is the only part that says which record collided.
        return "Ya existe un registro con esos datos: %s = %s.".formatted(field, value);
    }
}
