package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CreateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.UpdateCourseCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ICourseService {
    Course create(CreateCourseCommand command);
    Course update(UUID id, UpdateCourseCommand command);
    Course setHomeroomTeacher(UUID id, UUID teacherId);
    Course getById(UUID id);
    Page<Course> list(Integer academicYearId, Pageable pageable);
    void delete(UUID id);
}
