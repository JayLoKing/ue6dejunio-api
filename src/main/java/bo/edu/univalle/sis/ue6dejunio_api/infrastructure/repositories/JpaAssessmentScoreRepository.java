package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AssessmentScoreEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaAssessmentScoreRepository extends JpaRepository<AssessmentScoreEntity, UUID> {

    Optional<AssessmentScoreEntity> findByCourseEnrollment_IdAndEvent_Id(UUID courseEnrollmentId, UUID eventId);

    Optional<AssessmentScoreEntity> findByCourseEnrollment_IdAndCriterion_Id(UUID courseEnrollmentId,
                                                                            UUID criterionId);

    List<AssessmentScoreEntity> findByEvent_Id(UUID eventId);

    boolean existsByEvent_Id(UUID eventId);

    List<AssessmentScoreEntity> findByCriterion_Id(UUID criterionId);

    /**
     * One student's whole score list. Both targets are EAGER, and the event's own criterion with
     * them, so without this graph a roster of N scores costs N round trips instead of one.
     */
    @EntityGraph(attributePaths = {"event", "event.criterion", "criterion"})
    List<AssessmentScoreEntity> findByCourseEnrollment_Id(UUID courseEnrollmentId);

    /**
     * Scores reach a subject through either target, so both branches are spelled out: a criterion
     * scored directly has no event to walk through.
     *
     * <p>The joins are explicit LEFT JOINs on purpose. Implicit path navigation compiles to inner
     * joins, which would drop every directly scored row before the OR is ever evaluated.
     */
    @Query("""
        SELECT COUNT(s) > 0 FROM AssessmentScoreEntity s
        LEFT JOIN s.event ev
        LEFT JOIN ev.criterion evc
        LEFT JOIN evc.classGroup evcg
        LEFT JOIN s.criterion dc
        LEFT JOIN dc.classGroup dcg
        WHERE evcg.subject.id = :subjectId OR dcg.subject.id = :subjectId
        """)
    boolean existsBySubject(@Param("subjectId") UUID subjectId);

    @Query("""
        SELECT COUNT(s) > 0 FROM AssessmentScoreEntity s
        LEFT JOIN s.event ev
        WHERE ev.criterion.id = :criterionId OR s.criterion.id = :criterionId
        """)
    boolean existsByCriterion(@Param("criterionId") UUID criterionId);

    /**
     * Two-level average. The inner query collapses every criterion to a single score — the average
     * of its activity items, or the one direct score it carries — and only the outer query averages
     * those criteria into their dimension. Averaging the raw scores in one pass instead would let a
     * criterion with ten items outweigh one scored directly by ten to one.
     *
     * <p>Native because JPQL has no subquery in {@code FROM}.
     */
    @Query(value = """
        SELECT t.dimension AS dimension, AVG(t.criterion_avg) AS avg_score
        FROM (
            SELECT c.dimension AS dimension, AVG(s.score) AS criterion_avg
            FROM assessment_scores s
            LEFT JOIN assessment_events e ON e.id_assessment_event = s.id_assessment_event
            JOIN evaluation_criteria c ON c.id_criterion = COALESCE(s.id_criterion, e.id_criterion)
            WHERE s.id_course_enrollment = :courseEnrollmentId
              AND c.id_class_group = :classGroupId
              AND c.trimester = :trimester
            GROUP BY c.id_criterion, c.dimension
        ) t
        GROUP BY t.dimension
        """, nativeQuery = true)
    List<Object[]> dimensionAverageRows(@Param("courseEnrollmentId") UUID courseEnrollmentId,
                                        @Param("classGroupId") UUID classGroupId,
                                        @Param("trimester") Integer trimester);
}
