package bo.edu.univalle.sis.ue6dejunio_api.domain.models.user;

import java.util.Locale;

/**
 * The one rule for what an e-mail address is equal to.
 *
 * <p>An account is found, compared and stored by its address, and the web trims and lowercases what
 * it sends. The API has to agree with it everywhere, or an account stored in capitals cannot sign
 * in from the web and {@code Ana@x.com} and {@code ana@x.com} can be registered twice. Every place
 * that takes or compares an address goes through {@link #normalize}, so the rule lives here once.
 *
 * <p>{@link Locale#ROOT} on purpose: under a Turkish default locale {@code "I".toLowerCase()} is a
 * dotless {@code ı}, which would turn one address into two depending on the server.
 */
public final class Emails {

    private Emails() {}

    /** Trimmed and lowercased; {@code null} stays {@code null} so callers keep their own check. */
    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }
}
