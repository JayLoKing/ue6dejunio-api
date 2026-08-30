package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumAdaptationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaCurriculumAdaptationRepository extends JpaRepository<CurriculumAdaptationEntity, UUID> {
    boolean existsByCurriculumPlan_IdAndStudent_Id(UUID planId, UUID studentId);
    @EntityGraph(attributePaths = {"student"})
    Page<CurriculumAdaptationEntity> findByCurriculumPlan_Id(UUID planId, Pageable pageable);

    long countByCurriculumPlan_Id(UUID planId);

    /**
     * How many significant adaptations each of these plans holds, asked for a whole page at once.
     * A plan with none is absent from the result, which reads as zero.
     */
    @Query("""
        SELECT a.curriculumPlan.id AS planId, COUNT(a.id) AS total
        FROM CurriculumAdaptationEntity a
        WHERE a.curriculumPlan.id IN :planIds
        GROUP BY a.curriculumPlan.id
        """)
    List<JpaCurriculumPlanRepository.PlanCount> countsByPlan(
        @Param("planIds") Collection<UUID> planIds);
}
