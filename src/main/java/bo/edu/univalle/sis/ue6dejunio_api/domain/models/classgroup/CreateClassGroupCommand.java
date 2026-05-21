package bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup;

import java.util.List;
import java.util.UUID;

public record CreateClassGroupCommand(
    Integer gradeId,
    Integer parallelId,
    List<Assignment> assignments
) {
    public record Assignment(UUID subjectId, UUID teacherId) {}
}
