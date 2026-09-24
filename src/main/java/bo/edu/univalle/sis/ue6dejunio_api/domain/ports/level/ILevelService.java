package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.level;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.CreateLevelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.Level;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.UpdateLevelCommand;

public interface ILevelService {
    Level create(CreateLevelCommand command);

    Level update(Integer id, UpdateLevelCommand command);

    Level getById(Integer id);

    PageResult<Level> list(PageQuery pageQuery);

    void delete(Integer id);
}
