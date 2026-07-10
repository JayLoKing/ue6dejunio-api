package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaPlanProgressRepository extends JpaRepository<CurriculumPlanProgressEntity, UUID> {
    List<CurriculumPlanProgressEntity> findByCurriculumPlan_IdOrderByProgressDateDesc(UUID planId);
}
