package bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification;

/**
 * Why the school is writing to someone.
 *
 * <p>The type is the notification's subject: an inbox groups and filters by it, and each one reads
 * as a heading without anybody typing it. {@link #CUSTOM} is the exception and the reason
 * {@code subject} exists — what the catalog has no name for, the Director names himself.
 *
 * <p>Kept as a varchar in the database rather than a Postgres enum. This list grows every time the
 * school finds another reason to write, and {@code ALTER TYPE ... ADD VALUE} cannot run inside a
 * transaction.
 */
public enum NotificationType {

    /** A teacher handed a plan in; the Director has something to review. */
    PDC_PUBLISHED,
    PDC_APPROVED,
    PDC_OBSERVED,

    /** The Director's own, about the work he supervises. */
    NOTEBOOK,
    ATTENDANCE,
    PDC_PROGRESS,
    /** "Aproximese a direccion" — the one that has to arrive, which is why delivery is stamped. */
    SUMMONS,

    /** Anything the catalog does not name. The only type that requires a subject. */
    CUSTOM;

    /**
     * Every other type is its own subject, so a copy of the label in the row would be a second
     * place for the same words to live — and to disagree.
     */
    public boolean requiresSubject() {
        return this == CUSTOM;
    }
}
