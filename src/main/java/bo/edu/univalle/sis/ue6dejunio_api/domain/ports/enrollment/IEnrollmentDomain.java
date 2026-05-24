package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IEnrollmentDomain {
    boolean existsEnrollment(UUID studentId, UUID classGroupId);
    void saveEnrollment(UUID studentId, UUID classGroupId);
    Optional<UUID> findEnrollmentId(UUID studentId, UUID classGroupId);
    List<Student> studentsByTeacher(UUID teacherId, Integer yearId);
}
