package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.parallel;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;

import java.util.Optional;

public interface IParallelDomain {
    Parallel save(Parallel parallel);
    Optional<Parallel> findById(Integer id);
    boolean existsByName(String name);
    boolean hasClassGroups(Integer parallelId);
    PageResult<Parallel> list(PageQuery pageQuery);
    void deleteById(Integer id);
}
