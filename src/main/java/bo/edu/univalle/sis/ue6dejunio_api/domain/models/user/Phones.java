package bo.edu.univalle.sis.ue6dejunio_api.domain.models.user;

/**
 * The one rule for what a phone number is equal to, and for what counts as having none.
 *
 * <p>A number belongs to one user at most (V28), so every place that stores or compares one has to
 * agree on its spelling: {@code " 70000000"} and {@code "70000000"} are the same phone, and a blank
 * field is no phone at all. Left to each caller, a stray space would let two accounts hold one
 * number, and a blank string would sit in the column as if it were a value every phoneless user
 * shared.
 */
public final class Phones {

    private Phones() {}

    /** Trimmed; {@code null} and blank both come back {@code null}, which is how none is stored. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
