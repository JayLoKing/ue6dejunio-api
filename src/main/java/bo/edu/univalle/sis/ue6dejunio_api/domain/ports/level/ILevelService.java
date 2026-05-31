package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.level;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.CreateLevelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.Level;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.UpdateLevelCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ILevelService {
    Level create(CreateLevelCommand command);
    Level update(Integer id, UpdateLevelCommand command);
    Level getById(Integer id);
    Page<Level> list(Pageable pageable);
    void delete(Integer id);
}
