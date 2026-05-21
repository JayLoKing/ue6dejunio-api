package bo.edu.univalle.sis.ue6dejunio_api.application.services.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ClassGroupService implements IClassGroupService {

    private final IClassGroupDomain classGroupDomain;

    public ClassGroupService(IClassGroupDomain classGroupDomain) {
        this.classGroupDomain = classGroupDomain;
    }

    @Override
    @Transactional
    public List<ClassGroup> createCourse(CreateClassGroupCommand command) {
        Integer academicYearId = classGroupDomain.currentAcademicYearId();
        List<ClassGroup> created = new ArrayList<>();

        for (CreateClassGroupCommand.Assignment a : command.assignments()) {
            if (classGroupDomain.existsAssignment(a.subjectId(), command.gradeId(),
                    command.parallelId(), academicYearId)) {
                throw new DuplicateResourceException("class_group (materia ya asignada al curso)",
                    a.subjectId().toString());
            }
            created.add(classGroupDomain.createAssignment(
                command.gradeId(), command.parallelId(), academicYearId,
                a.subjectId(), a.teacherId()));
        }
        return created;
    }
}
