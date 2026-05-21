package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollResult;

public interface IEnrollmentService {
    EnrollResult enrollCourse(EnrollCourseCommand command);
}
