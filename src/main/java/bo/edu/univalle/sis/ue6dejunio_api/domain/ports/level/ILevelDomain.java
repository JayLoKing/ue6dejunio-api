package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.level;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.Level;
import java.util.Optional;

public interface ILevelDomain {
    Level save(Level level);

    Optional<Level> findById(Integer id);

    boolean existsByName(String name);

    boolean hasGrades(Integer levelId);

    PageResult<Level> list(PageQuery pageQuery);

    void deleteById(Integer id);
}
