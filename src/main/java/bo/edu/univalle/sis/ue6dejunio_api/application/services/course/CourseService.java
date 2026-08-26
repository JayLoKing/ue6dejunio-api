package bo.edu.univalle.sis.ue6dejunio_api.application.services.course;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CourseWithSubjects;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CreateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.UpdateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CourseService implements ICourseService {

    private final ICourseDomain courseDomain;
    private final IClassGroupService classGroupService;

    public CourseService(ICourseDomain courseDomain, IClassGroupService classGroupService) {
        this.courseDomain = courseDomain;
        this.classGroupService = classGroupService;
    }

    @Override
    @Transactional
    public CourseWithSubjects create(CreateCourseCommand c) {
        if (!courseDomain.gradeExists(c.gradeId())) {
            throw new ResourceNotFoundException("Grado", c.gradeId());
        }
        if (!courseDomain.parallelExists(c.parallelId())) {
            throw new ResourceNotFoundException("Paralelo", c.parallelId());
        }
        Integer yearId = courseDomain.currentAcademicYearId();
        if (courseDomain.existsByGradeParallelYear(c.gradeId(), c.parallelId(), yearId)) {
            throw new DuplicateResourceException("curso (grado+paralelo+anio)",
                c.gradeId() + "/" + c.parallelId() + "/" + yearId);
        }
        if (c.homeroomTeacherId() != null && !courseDomain.userIsNonTechnicalTeacher(c.homeroomTeacherId())) {
            throw new ConflictException("El docente de aula debe ser Teacher NO tecnico");
        }

        Course course = courseDomain.create(c.gradeId(), c.parallelId(), yearId, c.homeroomTeacherId());

        List<ClassGroup> classGroups = List.of();
        if (c.assignments() != null && !c.assignments().isEmpty()) {
            List<CreateClassGroupCommand.Assignment> assignments = c.assignments().stream()
                .map(a -> new CreateClassGroupCommand.Assignment(a.subjectId(), a.teacherId()))
                .toList();
            classGroups = classGroupService.createForCourse(
                new CreateClassGroupCommand(course.id(), assignments));
        }
        return new CourseWithSubjects(course, classGroups);
    }

    @Override
    @Transactional
    public Course update(UUID id, UpdateCourseCommand c) {
        getById(id);
        Course result = null;
        if (c.homeroomTeacherId() != null) {
            if (!courseDomain.userIsNonTechnicalTeacher(c.homeroomTeacherId())) {
                throw new ConflictException("El docente de aula debe ser Teacher NO tecnico");
            }
            result = courseDomain.setHomeroomTeacher(id, c.homeroomTeacherId());
        }
        if (c.active() != null) {
            result = courseDomain.setActive(id, c.active());
        }
        return result != null ? result : getById(id);
    }

    @Override
    @Transactional
    public Course setHomeroomTeacher(UUID id, UUID teacherId) {
        getById(id);
        if (!courseDomain.userIsNonTechnicalTeacher(teacherId)) {
            throw new ConflictException("El docente de aula debe ser Teacher NO tecnico");
        }
        return courseDomain.setHomeroomTeacher(id, teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Course> homeroomCourseOf(UUID teacherId) {
        return courseDomain.homeroomCourseOf(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public Course getById(UUID id) {
        return courseDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Course> list(Integer academicYearId, PageQuery pageQuery) {
        return courseDomain.list(academicYearId, pageQuery);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        courseDomain.setActive(id, false);
    }
}
