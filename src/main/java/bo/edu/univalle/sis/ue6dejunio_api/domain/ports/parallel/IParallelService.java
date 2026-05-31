package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.parallel;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.CreateParallelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.UpdateParallelCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IParallelService {
    Parallel create(CreateParallelCommand command);
    Parallel update(Integer id, UpdateParallelCommand command);
    Parallel getById(Integer id);
    Page<Parallel> list(Pageable pageable);
    void delete(Integer id);
}
