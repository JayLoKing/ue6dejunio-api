package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;

import java.util.UUID;

public record StudentDirectoryResponse(
    UUID id,
    String rudeCode,
    String fullName,
    String grade,
    String parallel,
    String level
) {
    public static StudentDirectoryResponse from(StudentDirectoryItem item) {
        return new StudentDirectoryResponse(
            item.id(), item.rudeCode(), item.fullName(), item.grade(), item.parallel(), item.level());
    }
}
