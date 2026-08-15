package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear;

/**
 * Auto-provisioning and "current academic year" resolution. The current academic year is always
 * the LATEST created one (max(year)) — never a fixed/first row.
 */
public interface IAcademicYearDomain {
    /** Idempotent: creates the academic_years row for {@code year} if missing, returns its id either way. */
    Integer ensureYear(int year);

    /** Id of the latest (current) academic year. Throws if none exist yet. */
    Integer currentYearId();
}
