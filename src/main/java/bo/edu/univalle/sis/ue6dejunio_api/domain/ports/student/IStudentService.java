package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;

import java.util.UUID;

public interface IStudentService {
    Student getById(UUID id);
}
