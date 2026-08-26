package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CourseWithSubjects;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CreateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.UpdateCourseCommand;

import java.util.Optional;
import java.util.UUID;

public interface ICourseService {
    CourseWithSubjects create(CreateCourseCommand command);
    Course update(UUID id, UpdateCourseCommand command);
    Course setHomeroomTeacher(UUID id, UUID teacherId);
    Course getById(UUID id);

    /** The course a teacher is homeroom of, if any. Empty is an answer, not an error. */
    Optional<Course> homeroomCourseOf(UUID teacherId);
    PageResult<Course> list(Integer academicYearId, PageQuery pageQuery);
    void delete(UUID id);
}
