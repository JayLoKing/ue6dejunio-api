package bo.edu.univalle.sis.ue6dejunio_api.application.services.level;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.CreateLevelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.Level;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.UpdateLevelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.level.ILevelDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.level.ILevelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LevelService implements ILevelService {

    private final ILevelDomain levelDomain;

    public LevelService(ILevelDomain levelDomain) {
        this.levelDomain = levelDomain;
    }

    @Override
    @Transactional
    public Level create(CreateLevelCommand command) {
        if (levelDomain.existsByName(command.name())) {
            throw new DuplicateResourceException("name", command.name());
        }
        return levelDomain.save(new Level(null, command.name()));
    }

    @Override
    @Transactional
    public Level update(Integer id, UpdateLevelCommand command) {
        Level current = getById(id);
        if (!current.name().equals(command.name()) && levelDomain.existsByName(command.name())) {
            throw new DuplicateResourceException("name", command.name());
        }
        return levelDomain.save(new Level(id, command.name()));
    }

    @Override
    @Transactional(readOnly = true)
    public Level getById(Integer id) {
        return levelDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Level", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Level> list(PageQuery pageQuery) {
        return levelDomain.list(pageQuery);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        getById(id);
        if (levelDomain.hasGrades(id)) {
            throw new ConflictException("No se puede eliminar: el nivel tiene grados asociados");
        }
        levelDomain.deleteById(id);
    }
}
