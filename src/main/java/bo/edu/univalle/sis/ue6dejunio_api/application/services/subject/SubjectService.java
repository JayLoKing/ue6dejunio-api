package bo.edu.univalle.sis.ue6dejunio_api.application.services.subject;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.CreateSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.UpdateSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.subject.ISubjectDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.subject.ISubjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SubjectService implements ISubjectService {

    private final ISubjectDomain subjectDomain;

    public SubjectService(ISubjectDomain subjectDomain) {
        this.subjectDomain = subjectDomain;
    }

    @Override
    @Transactional
    public Subject create(CreateSubjectCommand c) {
        if (!subjectDomain.areaExists(c.areaId())) {
            throw new ResourceNotFoundException("KnowledgeArea", c.areaId());
        }
        return subjectDomain.create(c.name(), c.areaId(), c.technical() != null && c.technical());
    }

    @Override
    @Transactional
    public Subject update(UUID id, UpdateSubjectCommand c) {
        getById(id);
        if (c.areaId() != null && !subjectDomain.areaExists(c.areaId())) {
            throw new ResourceNotFoundException("KnowledgeArea", c.areaId());
        }
        // Deactivating through update is the same act as deleting, and academic history says no the
        // same way. Guarding only delete left the rule open on the other path.
        if (Boolean.FALSE.equals(c.active()) && subjectDomain.hasScoresForSubject(id)) {
            throw new ConflictException(
                "no se pudo desactivar la materia porque tiene calificaciones registradas");
        }
        return subjectDomain.update(id, c.name(), c.areaId(), c.technical(), c.active());
    }

    @Override
    @Transactional(readOnly = true)
    public Subject getById(UUID id) {
        return subjectDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Subject", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Subject> list(PageQuery pageQuery) {
        return subjectDomain.list(pageQuery);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        if (subjectDomain.hasScoresForSubject(id)) {
            throw new ConflictException("no se pudo desactivar la materia porque tiene calificaciones registradas");
        }
        subjectDomain.deactivate(id);
    }
}
