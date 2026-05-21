package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;

import java.util.Optional;
import java.util.UUID;

public interface IStudentDomain {
    Student save(Student student);
    Optional<Student> findById(UUID id);
    Optional<Student> findByRudeCode(String rudeCode);
    Optional<Student> findByIdentityCard(String identityCard);
    boolean existsByRudeCode(String rudeCode);
    boolean existsByIdentityCard(String identityCard);
}
