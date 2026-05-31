package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.parallel;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface IParallelDomain {
    Parallel save(Parallel parallel);
    Optional<Parallel> findById(Integer id);
    boolean existsByName(String name);
    boolean hasClassGroups(Integer parallelId);
    Page<Parallel> list(Pageable pageable);
    void deleteById(Integer id);
}
