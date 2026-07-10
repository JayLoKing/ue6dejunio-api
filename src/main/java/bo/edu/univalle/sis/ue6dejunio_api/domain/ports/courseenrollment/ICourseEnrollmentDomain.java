package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ICourseEnrollmentDomain {
    boolean courseExists(UUID courseId);
    boolean existsEnrollment(UUID studentId, UUID courseId);
    void saveEnrollment(UUID studentId, UUID courseId);
    Optional<UUID> findByStudentAndCourse(UUID studentId, UUID courseId);
    Page<CourseStudent> studentsByCourse(UUID courseId, Pageable pageable);
    UUID courseOfEnrollment(UUID courseEnrollmentId);
    Optional<CourseStudent> courseStudentById(UUID courseEnrollmentId);
}
