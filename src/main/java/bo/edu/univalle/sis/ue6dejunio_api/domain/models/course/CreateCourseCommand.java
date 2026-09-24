package bo.edu.univalle.sis.ue6dejunio_api.domain.models.course;

import java.util.List;
import java.util.UUID;

public record CreateCourseCommand(
        Integer gradeId, Integer parallelId, UUID homeroomTeacherId, List<Assignment> assignments) {
    public record Assignment(UUID subjectId, UUID teacherId) {}
}
