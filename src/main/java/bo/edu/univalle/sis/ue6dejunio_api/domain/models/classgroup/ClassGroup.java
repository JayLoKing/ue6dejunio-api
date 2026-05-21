package bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup;

import java.util.UUID;

public record ClassGroup(
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
) {}
