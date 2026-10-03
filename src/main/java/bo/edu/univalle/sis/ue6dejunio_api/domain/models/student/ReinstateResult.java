package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.UUID;

/**
 * What a reinstatement did.
 *
 * @param studentId who came back
 * @param status the status they hold now
 * @param courseId the course they are seated in
 * @param enrollmentReactivated {@code true} when an enrolment they already held was reopened, so
 *     their history continues; {@code false} when a new one was created
 */
public record ReinstateResult(
        UUID studentId, String status, UUID courseId, boolean enrollmentReactivated) {}
