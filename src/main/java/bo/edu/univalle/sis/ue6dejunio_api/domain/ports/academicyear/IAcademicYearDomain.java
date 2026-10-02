package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.AcademicYearItem;
import java.util.Optional;

/**
 * Auto-provisioning and "current academic year" resolution. The current academic year is the one
 * flagged active (exactly one at a time, guaranteed by the database) — never the newest row.
 * Creating a year does not make it current.
 */
public interface IAcademicYearDomain {
    /**
     * Idempotent: creates the academic_years row for {@code year} if missing, returns its id either
     * way. The row is created inactive.
     */
    Integer ensureYear(int year);

    /** Id of the active academic year. Throws ResourceNotFoundException if none is active. */
    Integer currentYearId();

    /**
     * Fresh-install bootstrap: marks {@code yearId} active only when no year is active at all, so
     * an existing choice is never overridden. Returns whether it activated the year.
     */
    boolean activateIfNoneActive(int yearId);

    /** The year as the catalogue shows it, or empty when no such id exists. */
    Optional<AcademicYearItem> findItem(int academicYearId);

    /**
     * Makes {@code academicYearId} the one active year, clearing the previous one. The caller has
     * checked the year exists and is open.
     */
    void setActive(int academicYearId);
}
