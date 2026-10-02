package bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog;

/**
 * The outcome of asking for a gestión to exist.
 *
 * @param year the gestión, as the catalogue shows it
 * @param created whether this call made it; false when it was already there
 */
public record CreatedAcademicYear(AcademicYearItem year, boolean created) {}
