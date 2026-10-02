package bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog;

/**
 * One gestión, for the pickers that ask which year a listing is about.
 *
 * @param year the four digits the school calls it by. The id is what every filter binds
 * @param active the year the system works in when a request names none (exactly one at a time)
 * @param closed whether the year has been closed; independent of {@code active}
 */
public record AcademicYearItem(Integer id, Integer year, boolean active, boolean closed) {}
