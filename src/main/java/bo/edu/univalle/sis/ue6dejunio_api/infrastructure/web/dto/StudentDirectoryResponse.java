package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import java.util.UUID;

/**
 * @param grade the course the student sits in, or sat in before leaving. Null for a student with no
 *     enrolment at all
 * @param status "Effective" or "Withdrawn" — a listing that spans the school shows both
 * @param academicYear the gestión the grade and parallel are true of
 */
public record StudentDirectoryResponse(
        UUID id,
        String rudeCode,
        String identityCard,
        String fullName,
        String grade,
        String parallel,
        String level,
        String status,
        Integer academicYear) {
    public static StudentDirectoryResponse from(StudentDirectoryItem item) {
        return new StudentDirectoryResponse(
                item.id(),
                item.rudeCode(),
                item.identityCard(),
                item.fullName(),
                item.grade(),
                item.parallel(),
                item.level(),
                item.status(),
                item.academicYear());
    }
}
