package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import java.util.UUID;

/**
 * A student a roster load left out because the school had withdrawn them.
 *
 * @param studentId the student that was left out
 * @param fullName how the load names them, so the person reading the result can tell who it was
 */
public record SkippedStudent(UUID studentId, String fullName) {}
