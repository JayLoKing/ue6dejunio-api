package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.RiskPredictionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaRiskPredictionRepository extends JpaRepository<RiskPredictionEntity, UUID> {

    /**
     * The rows a run is about to overwrite, in one query.
     *
     * <p>A superset: it asks for every combination of the students and the subjects in the batch,
     * not for the exact pairs, because a pairwise {@code IN} over a few hundred tuples is a query
     * plan nobody wants to defend. The caller keeps the pairs it asked for and ignores the rest —
     * one bounded read either way, instead of one per student.
     */
    List<RiskPredictionEntity> findByTrimesterAndClassGroupIdInAndStudentIdIn(
        Integer trimester, Collection<UUID> classGroupIds, Collection<UUID> studentIds);

    /**
     * A named set of predictions by id, for the caller that already knows which rows it means.
     *
     * <p>Unordered on purpose: the caller holds its own ordering — these are the rows it just
     * wrote — and re-imposing the severity CASE here would suggest a ranking that is not what this
     * read is for.
     */
    @Query("""
        SELECT p, s.names, s.lastNames, g.subject.name
        FROM RiskPredictionEntity p, StudentEntity s, ClassGroupEntity g
        WHERE s.id = p.studentId AND g.id = p.classGroupId
          AND p.id IN :ids
        """)
    List<Object[]> findByIdsWithNames(@Param("ids") Collection<UUID> ids);

    /**
     * One subject's standing predictions, worst first.
     *
     * <p>The order lives in a CASE rather than in the column, because the column holds the model's
     * four words and they do not sort into severity alphabetically. Sorting them as text would put
     * {@code EnRiesgo} above {@code RiesgoCritico} and quietly bury the students the list exists
     * for. Ties break on the same name order every other listing in this system uses.
     */
    @Query("""
        SELECT p, s.names, s.lastNames, g.subject.name
        FROM RiskPredictionEntity p, StudentEntity s, ClassGroupEntity g
        WHERE s.id = p.studentId AND g.id = p.classGroupId
          AND p.classGroupId = :classGroupId AND p.trimester = :trimester
        ORDER BY CASE p.riskLevel
                     WHEN 'RiesgoCritico' THEN 0
                     WHEN 'EnRiesgo' THEN 1
                     WHEN 'SinRiesgo' THEN 2
                     ELSE 3
                 END,
                 s.lastNames, s.names
        """)
    List<Object[]> findByClassGroupWithNames(@Param("classGroupId") UUID classGroupId,
                                             @Param("trimester") Integer trimester);

    /** Every subject of one course, same ordering, plus the subject name to tell them apart. */
    @Query("""
        SELECT p, s.names, s.lastNames, g.subject.name
        FROM RiskPredictionEntity p, StudentEntity s, ClassGroupEntity g
        WHERE s.id = p.studentId AND g.id = p.classGroupId
          AND g.course.id = :courseId AND p.trimester = :trimester
        ORDER BY CASE p.riskLevel
                     WHEN 'RiesgoCritico' THEN 0
                     WHEN 'EnRiesgo' THEN 1
                     WHEN 'SinRiesgo' THEN 2
                     ELSE 3
                 END,
                 s.lastNames, s.names, g.subject.name
        """)
    List<Object[]> findByCourseWithNames(@Param("courseId") UUID courseId,
                                         @Param("trimester") Integer trimester);

    /** One student across every subject they sit, worst first, newest trimester first. */
    @Query("""
        SELECT p, s.names, s.lastNames, g.subject.name
        FROM RiskPredictionEntity p, StudentEntity s, ClassGroupEntity g
        WHERE s.id = p.studentId AND g.id = p.classGroupId
          AND p.studentId = :studentId
        ORDER BY p.trimester DESC,
                 CASE p.riskLevel
                     WHEN 'RiesgoCritico' THEN 0
                     WHEN 'EnRiesgo' THEN 1
                     WHEN 'SinRiesgo' THEN 2
                     ELSE 3
                 END,
                 g.subject.name
        """)
    List<Object[]> findByStudentWithNames(@Param("studentId") UUID studentId);
}
