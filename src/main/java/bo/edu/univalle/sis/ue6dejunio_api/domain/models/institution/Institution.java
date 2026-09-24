package bo.edu.univalle.sis.ue6dejunio_api.domain.models.institution;

/**
 * The heading every official document of the school prints: where it answers, what it is, and who
 * directs it.
 *
 * <p>Everything but the Director is what the school IS, not something it records — read from
 * configuration, because none of it varies by course, student or year, and a column for it would be
 * a column with one value in it forever. The Director is a person who holds a role and changes, so
 * it is read from the users. Absent when nobody holds the role, the way the paper form leaves the
 * line blank.
 *
 * @param district the district the school answers to.
 * @param school the school's own name.
 * @param directorName who directs it, or null when nobody holds the role.
 * @param department the department, printed by the libreta and the informe pedagógico.
 * @param dependency fiscal, private or by agreement — the libreta prints it.
 * @param shift morning or afternoon; the libreta prints it.
 * @param educationLevel the level of education, as the curriculum words it.
 */
public record Institution(
        String district,
        String school,
        String directorName,
        String department,
        String dependency,
        String shift,
        String educationLevel) {}
