package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.GradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaGradeRepository extends JpaRepository<GradeEntity, Integer> {
}
