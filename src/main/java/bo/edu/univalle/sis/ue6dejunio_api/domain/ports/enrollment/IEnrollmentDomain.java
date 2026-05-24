package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.TeacherStudent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface IEnrollmentDomain {
    boolean existsEnrollment(UUID studentId, UUID classGroupId);
    void saveEnrollment(UUID studentId, UUID classGroupId);
    Optional<UUID> findEnrollmentId(UUID studentId, UUID classGroupId);
    Page<TeacherStudent> studentsByTeacher(UUID teacherId, Integer yearId, Pageable pageable);
}
