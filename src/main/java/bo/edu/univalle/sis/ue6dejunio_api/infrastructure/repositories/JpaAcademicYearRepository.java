package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicYearEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaAcademicYearRepository extends JpaRepository<AcademicYearEntity, Integer> {
    Optional<AcademicYearEntity> findTopByOrderByYearDesc();
}
