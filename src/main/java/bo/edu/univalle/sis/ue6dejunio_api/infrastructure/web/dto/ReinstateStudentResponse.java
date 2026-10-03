package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.ReinstateResult;
import java.util.UUID;

/**
 * @param enrollmentReactivated {@code true} when an enrolment the student already held was
 *     reopened, so their history continues; {@code false} when a new one was created
 */
public record ReinstateStudentResponse(
        UUID studentId, String status, UUID courseId, boolean enrollmentReactivated) {
    public static ReinstateStudentResponse from(ReinstateResult result) {
        return new ReinstateStudentResponse(
                result.studentId(),
                result.status(),
                result.courseId(),
                result.enrollmentReactivated());
    }
}
