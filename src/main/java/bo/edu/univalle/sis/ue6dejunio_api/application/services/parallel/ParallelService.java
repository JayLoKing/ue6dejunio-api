package bo.edu.univalle.sis.ue6dejunio_api.application.services.parallel;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.CreateParallelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.UpdateParallelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.parallel.IParallelDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.parallel.IParallelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParallelService implements IParallelService {

    private final IParallelDomain parallelDomain;

    public ParallelService(IParallelDomain parallelDomain) {
        this.parallelDomain = parallelDomain;
    }

    @Override
    @Transactional
    public Parallel create(CreateParallelCommand c) {
        if (parallelDomain.existsByName(c.name())) {
            throw new DuplicateResourceException("name", c.name());
        }
        return parallelDomain.save(new Parallel(null, c.name()));
    }

    @Override
    @Transactional
    public Parallel update(Integer id, UpdateParallelCommand c) {
        Parallel current = getById(id);
        if (!current.name().equals(c.name()) && parallelDomain.existsByName(c.name())) {
            throw new DuplicateResourceException("name", c.name());
        }
        return parallelDomain.save(new Parallel(id, c.name()));
    }

    @Override
    @Transactional(readOnly = true)
    public Parallel getById(Integer id) {
        return parallelDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Parallel", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Parallel> list(PageQuery pageQuery) {
        return parallelDomain.list(pageQuery);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        getById(id);
        if (parallelDomain.hasClassGroups(id)) {
            throw new ConflictException("No se puede eliminar: el paralelo tiene class_groups asociados");
        }
        parallelDomain.deleteById(id);
    }
}
