package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;

import java.util.UUID;

public record ClassGroupResponse(
    UUID id,
    UUID courseId,
    String gradeName,
    String parallelName,
    UUID subjectId,
    String subjectName,
    UUID teacherId,
    String teacherName,
    boolean active
) {
    public static ClassGroupResponse from(ClassGroup c) {
        return new ClassGroupResponse(
            c.id(), c.courseId(), c.gradeName(), c.parallelName(),
            c.subjectId(), c.subjectName(), c.teacherId(), c.teacherName(), c.active());
    }
}
