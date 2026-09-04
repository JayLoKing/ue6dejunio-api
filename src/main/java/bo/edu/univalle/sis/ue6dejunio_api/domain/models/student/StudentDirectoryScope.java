package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.Optional;

/**
 * Which students a directory listing is about.
 *
 * <p>An enum rather than a raw status, because the third answer is not a status: "all" is the
 * absence of the filter, and a caller that had to send the word "all" as if it were one would be
 * one typo away from a listing that silently matched nothing.
 */
public enum StudentDirectoryScope {

    /** Still on the roll. What every caller meant before this filter existed, and the default. */
    ACTIVE("Effective"),

    /** Taken off the roll. The Director's listing of who left and why. */
    WITHDRAWN("Withdrawn"),

    /** Everyone the school ever enrolled. */
    ALL(null);

    private final String status;

    StudentDirectoryScope(String status) {
        this.status = status;
    }

    /** The status column value this scope narrows to, or {@code null} to narrow to nothing. */
    public String status() {
        return status;
    }

    /** Accepts the constant name, case-insensitive. Empty when the caller asked for nothing. */
    public static Optional<StudentDirectoryScope> fromRequestValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim();
        for (StudentDirectoryScope scope : values()) {
            if (scope.name().equalsIgnoreCase(normalized)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }
}
