package bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TeacherStudent(
    UUID id,
    String rudeCode,
    String identityCard,
    String names,
    String lastNames,
    LocalDate birthDate,
    String gender,
    String status,
    List<SubjectEnrollment> enrollments
) {
    public record SubjectEnrollment(
        UUID enrollmentId,
        UUID classGroupId,
        UUID subjectId,
        String subjectName
    ) {}
}
