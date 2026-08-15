package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import java.util.UUID;

public record CourseStudent(
    UUID courseEnrollmentId,
    UUID studentId,
    String rudeCode,
    String identityCard,
    String names,
    String lastNames,
    String status,
    String gender
) {
    public String fullName() {
        return names + " " + lastNames;
    }
}
