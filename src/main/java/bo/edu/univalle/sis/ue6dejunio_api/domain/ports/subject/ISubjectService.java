package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.subject;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.CreateSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.UpdateSubjectCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ISubjectService {
    Subject create(CreateSubjectCommand command);
    Subject update(UUID id, UpdateSubjectCommand command);
    Subject getById(UUID id);
    Page<Subject> list(Pageable pageable);
    void delete(UUID id);
}
