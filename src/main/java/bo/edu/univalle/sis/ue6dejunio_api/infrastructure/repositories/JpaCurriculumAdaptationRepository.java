package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumAdaptationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaCurriculumAdaptationRepository extends JpaRepository<CurriculumAdaptationEntity, UUID> {
    boolean existsByCurriculumPlan_IdAndStudent_Id(UUID planId, UUID studentId);
    @EntityGraph(attributePaths = {"student"})
    Page<CurriculumAdaptationEntity> findByCurriculumPlan_Id(UUID planId, Pageable pageable);
}
