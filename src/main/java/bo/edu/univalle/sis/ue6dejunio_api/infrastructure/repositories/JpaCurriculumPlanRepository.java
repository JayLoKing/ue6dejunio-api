package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JpaCurriculumPlanRepository extends JpaRepository<CurriculumPlanEntity, UUID> {

    boolean existsByClassGroup_IdAndTrimester(UUID classGroupId, Integer trimester);

    @Query("""
        SELECT p FROM CurriculumPlanEntity p
        WHERE (:classGroupId IS NULL OR p.classGroup.id = :classGroupId)
              AND (:trimester IS NULL OR p.trimester = :trimester)
              AND (:status IS NULL OR p.status = :status)
        """)
    Page<CurriculumPlanEntity> search(@Param("classGroupId") UUID classGroupId,
                                      @Param("trimester") Integer trimester,
                                      @Param("status") String status,
                                      Pageable pageable);
}
