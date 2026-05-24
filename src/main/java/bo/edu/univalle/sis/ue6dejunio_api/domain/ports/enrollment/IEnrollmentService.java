package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;

import java.util.List;
import java.util.UUID;

public interface IEnrollmentService {
    EnrollResult enrollCourse(EnrollCourseCommand command);
    List<Student> studentsOfTeacher(UUID teacherId);
}
