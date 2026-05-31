package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.LevelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaLevelRepository extends JpaRepository<LevelEntity, Integer> {
    boolean existsByName(String name);
}
