package bo.edu.univalle.sis.ue6dejunio_api.application.services.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ClassGroupService implements IClassGroupService {

    private final IClassGroupDomain classGroupDomain;

    public ClassGroupService(IClassGroupDomain classGroupDomain) {
        this.classGroupDomain = classGroupDomain;
    }

    @Override
    @Transactional
    public List<ClassGroup> createForCourse(CreateClassGroupCommand command) {
        if (!classGroupDomain.courseExists(command.courseId())) {
            throw new ResourceNotFoundException("Course", command.courseId());
        }
        List<ClassGroup> created = new ArrayList<>();
        for (CreateClassGroupCommand.Assignment a : command.assignments()) {
            if (!classGroupDomain.subjectExists(a.subjectId())) {
                throw new ResourceNotFoundException("Materia", a.subjectId());
            }
            if (a.teacherId() == null) {
                throw new IllegalArgumentException("Cada materia requiere un docente asignado");
            }
            boolean technicalSubject = classGroupDomain.subjectIsTechnical(a.subjectId());
            if (technicalSubject && !classGroupDomain.userIsTechnicalTeacher(a.teacherId())) {
                throw new IllegalArgumentException(
                    "Materia tecnica requiere docente tecnico: " + a.subjectId());
            }
            if (!technicalSubject && !classGroupDomain.userIsNonTechnicalTeacher(a.teacherId())) {
                throw new IllegalArgumentException(
                    "Materia no tecnica requiere docente de aula (no tecnico): " + a.subjectId());
            }
            if (classGroupDomain.existsByCourseAndSubject(command.courseId(), a.subjectId())) {
                throw new DuplicateResourceException("class_group (materia ya asignada al curso)",
                    a.subjectId().toString());
            }
            created.add(classGroupDomain.create(command.courseId(), a.subjectId(), a.teacherId()));
        }
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassGroup> byCourse(UUID courseId) {
        return classGroupDomain.byCourse(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassGroup> byTeacher(UUID teacherId) {
        return classGroupDomain.byTeacher(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassGroup getById(UUID id) {
        return classGroupDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ClassGroup", id));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        classGroupDomain.setActive(id, false);
    }
}
