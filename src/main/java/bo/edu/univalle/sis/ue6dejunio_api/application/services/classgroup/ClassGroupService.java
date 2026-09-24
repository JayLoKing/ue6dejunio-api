package bo.edu.univalle.sis.ue6dejunio_api.application.services.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                throw new ValidationException("Cada materia requiere un docente asignado");
            }
            validateTeacherMatchesSubject(command.courseId(), a.subjectId(), a.teacherId());
            if (classGroupDomain.existsByCourseAndSubject(command.courseId(), a.subjectId())) {
                throw new DuplicateResourceException(
                        "class_group (materia ya asignada al curso)", a.subjectId().toString());
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
        return classGroupDomain
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassGroup", id));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        classGroupDomain.setActive(id, false);
    }

    @Override
    @Transactional
    public ClassGroup reassignTeacher(UUID courseId, UUID classGroupId, UUID teacherId) {
        ClassGroup cg =
                classGroupDomain
                        .findById(classGroupId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("ClassGroup", classGroupId));
        if (!cg.courseId().equals(courseId)) {
            throw new ResourceNotFoundException("ClassGroup", classGroupId);
        }
        if (!classGroupDomain.userIsTeacher(teacherId)) {
            throw new ResourceNotFoundException("Docente", teacherId);
        }
        validateTeacherMatchesSubject(courseId, cg.subjectId(), teacherId);
        return classGroupDomain.setTeacher(classGroupId, teacherId);
    }

    /**
     * Who may stand in front of a subject.
     *
     * <p>A technical subject belongs to a technical teacher, or to the teacher who runs this
     * course. The school does not have enough technical teachers to cover every course, and the gap
     * is filled by the homeroom teacher rather than leaving the subject with nobody in front of it
     * — a rule that refuses that does not protect anything, it just describes a course that cannot
     * be created.
     *
     * <p>The licence is over their own course only: an aula teacher from another course is still
     * refused, and it is the course being assigned that decides, which is why this needs to know
     * which one it is.
     *
     * <p>Nothing opens in the other direction. A non-technical subject still belongs to an aula
     * teacher, because there the shortage does not exist.
     */
    private void validateTeacherMatchesSubject(UUID courseId, UUID subjectId, UUID teacherId) {
        if (classGroupDomain.subjectIsTechnical(subjectId)) {
            if (!classGroupDomain.userIsTechnicalTeacher(teacherId)
                    && !classGroupDomain.userIsHomeroomTeacherOf(teacherId, courseId)) {
                throw new ConflictException(
                        "Materia tecnica: requiere un docente tecnico o el "
                                + "docente de aula del curso: "
                                + subjectId);
            }
            return;
        }
        if (!classGroupDomain.userIsNonTechnicalTeacher(teacherId)) {
            throw new ConflictException(
                    "Materia no tecnica requiere docente de aula (no tecnico): " + subjectId);
        }
    }
}
