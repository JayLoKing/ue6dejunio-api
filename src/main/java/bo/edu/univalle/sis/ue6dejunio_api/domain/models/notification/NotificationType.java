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

    /**
     * A student was taken off the roll, and the teachers who ran their courses are being told.
     *
     * <p>Written by the withdrawal itself. A teacher whose roster shrinks overnight otherwise has
     * to go and ask why, and by hand this would be a claim about a decision nobody can check.
     */
    STUDENT_WITHDRAWN,

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

    /**
     * Whether a person may write this one, or only the change itself can.
     *
     * <p>The three PDC types are statements of fact about a review that happened, and the listener
     * is what makes them true. Posted by hand they become a claim nobody checked: a teacher would
     * read that their plan was approved while it still sat unreviewed, with the inbox saying one
     * thing and the plan another and no way to tell which. A withdrawal is the same shape — the
     * student's row is what makes it true.
     */
    public boolean writtenByHand() {
        return switch (this) {
            case PDC_PUBLISHED, PDC_APPROVED, PDC_OBSERVED, STUDENT_WITHDRAWN -> false;
            case NOTEBOOK, ATTENDANCE, PDC_PROGRESS, SUMMONS, CUSTOM -> true;
        };
    }
}
