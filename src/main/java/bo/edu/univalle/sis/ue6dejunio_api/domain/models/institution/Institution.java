package bo.edu.univalle.sis.ue6dejunio_api.domain.models.institution;

/**
 * The heading every official document of the school prints: which district it answers to, its own
 * name, and who directs it.
 *
 * <p>The district and the school are what the school IS, not something it records — they are read
 * from configuration. The Director is a person who holds a role and changes, so it is read from the
 * users. Absent when nobody holds the role, the way the paper form leaves the line blank.
 */
public record Institution(String district, String school, String directorName) {}
