package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import java.time.LocalDate;
import java.util.UUID;

public record CourseEnrollment(
        UUID id,
        UUID studentId,
        String studentName,
        UUID courseId,
        LocalDate enrollmentDate,
        String status) {}
