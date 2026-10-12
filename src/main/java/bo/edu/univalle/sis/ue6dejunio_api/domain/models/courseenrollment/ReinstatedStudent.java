package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import java.util.UUID;

/**
 * A student a roster load took back: withdrawn in an earlier gestión, named again on this one's
 * roster, returned to Effective and seated.
 *
 * @param studentId the student that was reinstated
 * @param fullName how the load names them
 */
public record ReinstatedStudent(UUID studentId, String fullName) {}
