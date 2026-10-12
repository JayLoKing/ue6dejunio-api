package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import java.util.UUID;

/**
 * A student a roster load left out because they already sit in another course of the same gestión.
 *
 * @param studentId the student that was left out
 * @param rudeCode their RUDE code, the identifier the official roster prints
 * @param fullName how the load names them
 * @param courseLabel the course they already sit in, as a teacher names it
 */
public record EnrolledElsewhere(
        UUID studentId, String rudeCode, String fullName, String courseLabel) {}
