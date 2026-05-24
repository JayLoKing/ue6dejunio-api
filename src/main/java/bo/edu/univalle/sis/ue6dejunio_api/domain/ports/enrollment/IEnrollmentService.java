package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.TeacherStudent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IEnrollmentService {
    EnrollResult enrollCourse(EnrollCourseCommand command);
    Page<TeacherStudent> studentsOfTeacher(UUID teacherId, Pageable pageable);
}
