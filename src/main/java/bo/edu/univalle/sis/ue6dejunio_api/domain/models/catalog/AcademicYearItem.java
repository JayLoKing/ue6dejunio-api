package bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog;

/**
 * One gestión, for the pickers that ask which year a listing is about.
 *
 * @param year the four digits the school calls it by. The id is what every filter binds
 */
public record AcademicYearItem(Integer id, Integer year) {}
