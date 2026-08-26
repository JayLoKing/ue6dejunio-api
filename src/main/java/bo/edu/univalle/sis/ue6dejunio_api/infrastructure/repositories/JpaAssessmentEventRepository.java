package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AssessmentEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaAssessmentEventRepository extends JpaRepository<AssessmentEventEntity, UUID> {
    List<AssessmentEventEntity> findByCriterion_IdOrderByCreatedAt(UUID criterionId);

    boolean existsByCriterion_Id(UUID criterionId);

    long countByCriterion_Id(UUID criterionId);
}
