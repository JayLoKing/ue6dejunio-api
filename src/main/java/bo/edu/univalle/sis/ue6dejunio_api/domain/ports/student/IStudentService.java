package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.BatchResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;

import java.util.List;
import java.util.UUID;

public interface IStudentService {
    Student create(CreateStudentCommand command);
    BatchResult createBatch(List<CreateStudentCommand> commands);
    Student getById(UUID id);
}
