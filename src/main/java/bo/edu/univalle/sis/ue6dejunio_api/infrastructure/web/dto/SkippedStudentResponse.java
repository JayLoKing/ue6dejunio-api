package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.SkippedStudent;
import java.util.UUID;

/** A withdrawn student a roster load left out. */
public record SkippedStudentResponse(UUID studentId, String fullName) {
    public static SkippedStudentResponse from(SkippedStudent s) {
        return new SkippedStudentResponse(s.studentId(), s.fullName());
    }
}
