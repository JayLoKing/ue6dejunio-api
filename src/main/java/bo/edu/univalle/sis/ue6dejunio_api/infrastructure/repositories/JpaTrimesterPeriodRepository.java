package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.TrimesterPeriodEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaTrimesterPeriodRepository extends JpaRepository<TrimesterPeriodEntity, UUID> {
    List<TrimesterPeriodEntity> findByAcademicYear_IdOrderByTrimester(Integer academicYearId);

    boolean existsByAcademicYear_IdAndTrimester(Integer academicYearId, Integer trimester);
}
