package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.subject;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.CreateSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.UpdateSubjectCommand;
import java.util.UUID;

public interface ISubjectService {
    Subject create(CreateSubjectCommand command);

    Subject update(UUID id, UpdateSubjectCommand command);

    Subject getById(UUID id);

    PageResult<Subject> list(PageQuery pageQuery);

    void delete(UUID id);
}
