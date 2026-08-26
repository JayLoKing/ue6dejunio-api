package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.parallel;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.CreateParallelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.UpdateParallelCommand;

public interface IParallelService {
    Parallel create(CreateParallelCommand command);
    Parallel update(Integer id, UpdateParallelCommand command);
    Parallel getById(Integer id);
    PageResult<Parallel> list(PageQuery pageQuery);
    void delete(Integer id);
}
