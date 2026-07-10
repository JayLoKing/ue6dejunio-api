package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;

import java.util.List;
import java.util.UUID;

public interface IClassGroupService {
    List<ClassGroup> createForCourse(CreateClassGroupCommand command);
    List<ClassGroup> byCourse(UUID courseId);
    List<ClassGroup> byTeacher(UUID teacherId);
    ClassGroup getById(UUID id);
    void delete(UUID id);
}
