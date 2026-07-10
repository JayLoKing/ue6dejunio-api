package bo.edu.univalle.sis.ue6dejunio_api.application.services.course;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CreateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.UpdateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CourseService implements ICourseService {

    private final ICourseDomain courseDomain;

    public CourseService(ICourseDomain courseDomain) {
        this.courseDomain = courseDomain;
    }

    @Override
    @Transactional
    public Course create(CreateCourseCommand c) {
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
            throw new IllegalArgumentException("El docente de aula debe ser Teacher NO tecnico");
        }
        return courseDomain.create(c.gradeId(), c.parallelId(), yearId, c.homeroomTeacherId());
    }

    @Override
    @Transactional
    public Course update(UUID id, UpdateCourseCommand c) {
        getById(id);
        Course result = null;
        if (c.homeroomTeacherId() != null) {
            if (!courseDomain.userIsNonTechnicalTeacher(c.homeroomTeacherId())) {
                throw new IllegalArgumentException("El docente de aula debe ser Teacher NO tecnico");
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
            throw new IllegalArgumentException("El docente de aula debe ser Teacher NO tecnico");
        }
        return courseDomain.setHomeroomTeacher(id, teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public Course getById(UUID id) {
        return courseDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Course> list(Integer academicYearId, Pageable pageable) {
        return courseDomain.list(academicYearId, pageable);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        courseDomain.setActive(id, false);
    }
}
