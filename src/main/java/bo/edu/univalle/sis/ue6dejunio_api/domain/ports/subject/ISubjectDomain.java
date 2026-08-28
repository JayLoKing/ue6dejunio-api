package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.subject;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;

import java.util.Optional;
import java.util.UUID;

public interface ISubjectDomain {
    Subject create(String name, Integer areaId, boolean technical);
    Subject update(UUID id, String name, Integer areaId, Boolean technical, Boolean active);
    Optional<Subject> findById(UUID id);
    PageResult<Subject> list(PageQuery pageQuery);
    void deactivate(UUID id);
    boolean hasScoresForSubject(UUID id);
    /** Whether the knowledge area exists. Every subject must land in one, so a bad id is a 404. */
    boolean areaExists(Integer areaId);
}
