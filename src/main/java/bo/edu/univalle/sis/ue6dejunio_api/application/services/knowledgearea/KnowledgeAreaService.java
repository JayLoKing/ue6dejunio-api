package bo.edu.univalle.sis.ue6dejunio_api.application.services.knowledgearea;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.CreateKnowledgeAreaCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.KnowledgeArea;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.UpdateKnowledgeAreaCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.knowledgearea.IKnowledgeAreaDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.knowledgearea.IKnowledgeAreaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeAreaService implements IKnowledgeAreaService {

    private final IKnowledgeAreaDomain areaDomain;

    public KnowledgeAreaService(IKnowledgeAreaDomain areaDomain) {
        this.areaDomain = areaDomain;
    }

    @Override
    @Transactional
    public KnowledgeArea create(CreateKnowledgeAreaCommand command) {
        if (areaDomain.existsByName(command.name())) {
            throw new DuplicateResourceException("name", command.name());
        }
        // Naming an area and deciding where it prints are two different thoughts. Said nothing
        // about, it goes after the ones already there.
        Integer order =
                command.displayOrder() != null
                        ? command.displayOrder()
                        : areaDomain.maxDisplayOrder() + 1;
        return areaDomain.save(new KnowledgeArea(null, command.name(), order));
    }

    @Override
    @Transactional
    public KnowledgeArea update(Integer id, UpdateKnowledgeAreaCommand command) {
        KnowledgeArea current = getById(id);
        if (!current.name().equals(command.name()) && areaDomain.existsByName(command.name())) {
            throw new DuplicateResourceException("name", command.name());
        }
        // An edit that says nothing about the order is not asking for it to be reset.
        Integer order =
                command.displayOrder() != null ? command.displayOrder() : current.displayOrder();
        return areaDomain.save(new KnowledgeArea(id, command.name(), order));
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeArea getById(Integer id) {
        return areaDomain
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArea", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<KnowledgeArea> list(PageQuery pageQuery) {
        return areaDomain.list(pageQuery);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        getById(id);
        // Every subject has to name an area, and the database says so with ON DELETE RESTRICT.
        // Answered here so the school reads what it has to do first instead of a 500.
        if (areaDomain.hasSubjects(id)) {
            throw new ConflictException(
                    "No se puede eliminar: el area de saberes tiene materias asociadas");
        }
        areaDomain.deleteById(id);
    }
}
