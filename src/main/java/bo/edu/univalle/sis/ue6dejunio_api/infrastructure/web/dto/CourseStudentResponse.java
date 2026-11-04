package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import java.util.UUID;

/**
 * @param incomplete whether RUDE, CI, birth date or gender is still unknown
 */
public record CourseStudentResponse(
        UUID courseEnrollmentId,
        UUID studentId,
        String rudeCode,
        String identityCard,
        String fullName,
        String status,
        String gender,
        boolean incomplete) {
    public static CourseStudentResponse from(CourseStudent s) {
        return new CourseStudentResponse(
                s.courseEnrollmentId(),
                s.studentId(),
                s.rudeCode(),
                s.identityCard(),
                s.fullName(),
                s.status(),
                s.gender(),
                s.incomplete());
    }
}
