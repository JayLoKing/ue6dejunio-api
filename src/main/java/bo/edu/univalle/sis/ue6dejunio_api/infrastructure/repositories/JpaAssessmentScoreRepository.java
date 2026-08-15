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

    boolean existsByEvent_Criterion_ClassGroup_Subject_Id(UUID subjectId);

    boolean existsByEvent_Criterion_Id(UUID criterionId);

    @Query("""
        SELECT s.event.criterion.dimension, AVG(s.score)
        FROM AssessmentScoreEntity s
        WHERE s.courseEnrollment.id = :courseEnrollmentId
              AND s.event.criterion.classGroup.id = :classGroupId
              AND s.event.criterion.trimester = :trimester
        GROUP BY s.event.criterion.dimension
        """)
    List<Object[]> dimensionAverageRows(@Param("courseEnrollmentId") UUID courseEnrollmentId,
                                        @Param("classGroupId") UUID classGroupId,
                                        @Param("trimester") Integer trimester);
}
