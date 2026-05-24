package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.TeacherStudent;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TeacherStudentResponse(
    UUID id,
    String rudeCode,
    String identityCard,
    String names,
    String lastNames,
    LocalDate birthDate,
    String gender,
    String status,
    List<Enrollment> enrollments
) {
    public record Enrollment(UUID enrollmentId, UUID classGroupId, UUID subjectId, String subjectName) {}

    public static TeacherStudentResponse from(TeacherStudent t) {
        List<Enrollment> en = t.enrollments().stream()
            .map(e -> new Enrollment(e.enrollmentId(), e.classGroupId(), e.subjectId(), e.subjectName()))
            .toList();
        return new TeacherStudentResponse(
            t.id(), t.rudeCode(), t.identityCard(), t.names(), t.lastNames(),
            t.birthDate(), t.gender(), t.status(), en);
    }
}
