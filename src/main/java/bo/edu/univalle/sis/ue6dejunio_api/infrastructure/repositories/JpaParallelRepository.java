package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ParallelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaParallelRepository extends JpaRepository<ParallelEntity, Integer> {
}
