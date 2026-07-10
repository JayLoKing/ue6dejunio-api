package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AssessmentScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaAssessmentScoreRepository extends JpaRepository<AssessmentScoreEntity, UUID> {

    Optional<AssessmentScoreEntity> findByCourseEnrollment_IdAndEvent_Id(UUID courseEnrollmentId, UUID eventId);

    List<AssessmentScoreEntity> findByEvent_Id(UUID eventId);

    List<AssessmentScoreEntity> findByCourseEnrollment_Id(UUID courseEnrollmentId);

    @Query("""
        SELECT ec.dimension, ec.maxWeight, AVG(s.score)
        FROM EvaluationCriterionEntity ec
        JOIN AssessmentEventEntity ev ON ev.criterion.id = ec.id
        LEFT JOIN AssessmentScoreEntity s ON s.event.id = ev.id
              AND s.courseEnrollment.id = :courseEnrollmentId
        WHERE ec.classGroup.id = :classGroupId AND ec.trimester = :trimester
        GROUP BY ec.id, ec.dimension, ec.maxWeight
        """)
    List<Object[]> consolidationRows(@Param("courseEnrollmentId") UUID courseEnrollmentId,
                                     @Param("classGroupId") UUID classGroupId,
                                     @Param("trimester") Integer trimester);
}
