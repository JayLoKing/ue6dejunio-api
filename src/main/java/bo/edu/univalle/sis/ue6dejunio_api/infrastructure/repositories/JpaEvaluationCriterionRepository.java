package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.EvaluationCriterionEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaEvaluationCriterionRepository extends JpaRepository<EvaluationCriterionEntity, UUID> {

    /**
     * The graph is not optional: {@code curriculumPlan} is a nullable LAZY to-one, which Hibernate
     * cannot proxy — it has to hit the database to learn whether the row is null. Mapping N
     * criteria would then cost N extra selects.
     */
    @EntityGraph(attributePaths = {"classGroup", "curriculumPlan"})
    @Query("""
        SELECT c FROM EvaluationCriterionEntity c
        WHERE c.classGroup.id = :classGroupId AND c.trimester = :trimester
              AND (:dimension IS NULL OR c.dimension = :dimension)
        ORDER BY c.dimension, c.name
        """)
    List<EvaluationCriterionEntity> search(@Param("classGroupId") UUID classGroupId,
                                           @Param("trimester") Integer trimester,
                                           @Param("dimension") String dimension);
}
