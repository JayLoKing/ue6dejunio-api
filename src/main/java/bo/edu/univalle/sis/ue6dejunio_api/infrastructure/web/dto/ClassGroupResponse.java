package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;

import java.util.UUID;

public record ClassGroupResponse(
    UUID id,
    UUID subjectId,
    String subjectName,
    UUID teacherId,
    String teacherName,
    Integer gradeId,
    String gradeName,
    Integer parallelId,
    String parallelName,
    Integer academicYearId,
    Integer year
) {
    public static ClassGroupResponse from(ClassGroup c) {
        return new ClassGroupResponse(
            c.id(), c.subjectId(), c.subjectName(), c.teacherId(), c.teacherName(),
            c.gradeId(), c.gradeName(), c.parallelId(), c.parallelName(),
            c.academicYearId(), c.year()
        );
    }
}
