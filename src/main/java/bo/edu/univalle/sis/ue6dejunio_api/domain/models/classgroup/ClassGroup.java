package bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup;

import java.util.UUID;

public record ClassGroup(
    UUID id,
    UUID courseId,
    String gradeName,
    String parallelName,
    UUID subjectId,
    String subjectName,
    UUID teacherId,
    String teacherName,
    boolean active
) {}
