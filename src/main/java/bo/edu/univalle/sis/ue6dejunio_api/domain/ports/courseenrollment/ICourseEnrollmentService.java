package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ICourseEnrollmentService {
    EnrollResult enroll(EnrollToCourseCommand command);
    Page<CourseStudent> studentsOfCourse(UUID courseId, Pageable pageable);
}
