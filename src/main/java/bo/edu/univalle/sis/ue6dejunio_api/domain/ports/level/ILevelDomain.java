package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.level;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.Level;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ILevelDomain {
    Level save(Level level);
    Optional<Level> findById(Integer id);
    boolean existsByName(String name);
    boolean hasGrades(Integer levelId);
    Page<Level> list(Pageable pageable);
    void deleteById(Integer id);
}
