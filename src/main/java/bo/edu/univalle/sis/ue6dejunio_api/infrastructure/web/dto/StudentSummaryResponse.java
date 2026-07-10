package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentTrimesterSummary;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StudentSummaryResponse(
    UUID courseEnrollmentId,
    UUID studentId,
    String fullName,
    Integer trimester,
    List<Subject> subjects,
    BigDecimal generalAverage
) {
    public record Subject(UUID classGroupId, String subjectName, BigDecimal total, boolean graded) {}

    public static StudentSummaryResponse from(StudentTrimesterSummary s) {
        List<Subject> subs = s.subjects().stream()
            .map(x -> new Subject(x.classGroupId(), x.subjectName(), x.total(), x.graded())).toList();
        return new StudentSummaryResponse(s.courseEnrollmentId(), s.studentId(), s.fullName(),
            s.trimester(), subs, s.generalAverage());
    }
}
