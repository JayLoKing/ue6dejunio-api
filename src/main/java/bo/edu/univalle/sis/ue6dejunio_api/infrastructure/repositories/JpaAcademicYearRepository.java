package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicYearEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaAcademicYearRepository extends JpaRepository<AcademicYearEntity, Integer> {
    Optional<AcademicYearEntity> findTopByOrderByYearDesc();
    Optional<AcademicYearEntity> findByYear(Integer year);

    /**
     * Newest first: the gestión a school works in is the one it opens a picker to select, and every
     * year before it is history somebody scrolls back to.
     */
    List<AcademicYearEntity> findAllByOrderByYearDesc();
}
