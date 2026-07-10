package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;

import java.util.UUID;

public record CourseStudentResponse(
    UUID courseEnrollmentId,
    UUID studentId,
    String rudeCode,
    String identityCard,
    String fullName,
    String status
) {
    public static CourseStudentResponse from(CourseStudent s) {
        return new CourseStudentResponse(
            s.courseEnrollmentId(), s.studentId(), s.rudeCode(), s.identityCard(),
            s.fullName(), s.status());
    }
}
