package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.subject;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ISubjectDomain {
    Subject create(String name, String area);
    Subject update(UUID id, String name, String area, Boolean active);
    Optional<Subject> findById(UUID id);
    boolean usedInClassGroups(UUID id);
    Page<Subject> list(Pageable pageable);
    void deactivate(UUID id);
}
