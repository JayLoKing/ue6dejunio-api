package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;
import java.util.List;
import java.util.UUID;

public interface IClassGroupService {
    List<ClassGroup> createForCourse(CreateClassGroupCommand command);

    List<ClassGroup> byCourse(UUID courseId);

    /**
     * The class groups the teacher runs in the active gestión. A teacher works in the active year
     * only; a closed year's class groups are the Director's to look back on, not the teacher's to
     * act on. Empty when no gestión is active.
     */
    List<ClassGroup> byTeacher(UUID teacherId);

    ClassGroup getById(UUID id);

    void delete(UUID id);

    ClassGroup reassignTeacher(UUID courseId, UUID classGroupId, UUID teacherId);
}
