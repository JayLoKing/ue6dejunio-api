package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import java.util.Collection;
import java.util.List;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaStudentRepository extends JpaRepository<StudentEntity, UUID> {

    /**
     * The three reads that turn a row into a {@code Student}, each asking for the author of its
     * last status change in the same query.
     *
     * <p>The mapper always resolves that author's name, so without the graph a lazy association
     * would be initialised one student at a time — and these two are the enrolment import, which
     * looks students up by the hundred precisely to avoid a query per row.
     */
    @Override
    @EntityGraph(attributePaths = "statusChangedBy")
    Optional<StudentEntity> findById(UUID id);

    @EntityGraph(attributePaths = "statusChangedBy")
    List<StudentEntity> findByRudeCodeIn(Collection<String> rudeCodes);

    @EntityGraph(attributePaths = "statusChangedBy")
    List<StudentEntity> findByIdentityCardIn(Collection<String> identityCards);

    /*
     * The two directory queries below repeat their FROM and their filters on purpose: the only
     * difference is the name match, and it stays out of the listing one because binding a null
     * into a LIKE is what the adapter's blank-q split exists to avoid.
     *
     * The enrolment is joined on `ce.status = s.status` rather than on 'Effective'. A withdrawal
     * closes the student's enrolments in the same transaction that closes the student, so this
     * reads as "the enrolment that matches what the student now is" — and it is what lets the
     * Director filter withdrawn students by the grade they were in. Joined on 'Effective' alone,
     * every withdrawn row came back with no course at all and the grade filter excluded them alL.
     */

    @Query(value = """
        SELECT DISTINCT new bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem(
            s.id, s.rudeCode, s.identityCard, CONCAT(s.names, ' ', s.lastNames),
            g.name, p.name, l.name, s.status)
        FROM StudentEntity s
        LEFT JOIN CourseEnrollmentEntity ce ON ce.student = s AND ce.status = s.status
        LEFT JOIN ce.course c
        LEFT JOIN c.grade g
        LEFT JOIN c.parallel p
        LEFT JOIN g.level l
        WHERE (:status IS NULL OR s.status = :status)
              AND (:courseId IS NULL OR c.id = :courseId)
              AND (:gradeId IS NULL OR g.id = :gradeId)
              AND (:parallelId IS NULL OR p.id = :parallelId)
        """,
        countQuery = """
        SELECT COUNT(DISTINCT s) FROM StudentEntity s
        LEFT JOIN CourseEnrollmentEntity ce ON ce.student = s AND ce.status = s.status
        LEFT JOIN ce.course c
        LEFT JOIN c.grade g
        LEFT JOIN c.parallel p
        WHERE (:status IS NULL OR s.status = :status)
              AND (:courseId IS NULL OR c.id = :courseId)
              AND (:gradeId IS NULL OR g.id = :gradeId)
              AND (:parallelId IS NULL OR p.id = :parallelId)
        """)
    Page<StudentDirectoryItem> listDirectory(@Param("courseId") UUID courseId,
                                             @Param("gradeId") Integer gradeId,
                                             @Param("parallelId") Integer parallelId,
                                             @Param("status") String status,
                                             Pageable pageable);

    @Query(value = """
        SELECT DISTINCT new bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem(
            s.id, s.rudeCode, s.identityCard, CONCAT(s.names, ' ', s.lastNames),
            g.name, p.name, l.name, s.status)
        FROM StudentEntity s
        LEFT JOIN CourseEnrollmentEntity ce ON ce.student = s AND ce.status = s.status
        LEFT JOIN ce.course c
        LEFT JOIN c.grade g
        LEFT JOIN c.parallel p
        LEFT JOIN g.level l
        WHERE (:status IS NULL OR s.status = :status)
              AND (:courseId IS NULL OR c.id = :courseId)
              AND (:gradeId IS NULL OR g.id = :gradeId)
              AND (:parallelId IS NULL OR p.id = :parallelId)
              AND (LOWER(s.names) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.lastNames) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.rudeCode) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.identityCard) LIKE LOWER(CONCAT('%', :q, '%')))
        """,
        countQuery = """
        SELECT COUNT(DISTINCT s) FROM StudentEntity s
        LEFT JOIN CourseEnrollmentEntity ce ON ce.student = s AND ce.status = s.status
        LEFT JOIN ce.course c
        LEFT JOIN c.grade g
        LEFT JOIN c.parallel p
        WHERE (:status IS NULL OR s.status = :status)
              AND (:courseId IS NULL OR c.id = :courseId)
              AND (:gradeId IS NULL OR g.id = :gradeId)
              AND (:parallelId IS NULL OR p.id = :parallelId)
              AND (LOWER(s.names) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.lastNames) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.rudeCode) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.identityCard) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<StudentDirectoryItem> searchDirectory(@Param("q") String q,
                                               @Param("courseId") UUID courseId,
                                               @Param("gradeId") Integer gradeId,
                                               @Param("parallelId") Integer parallelId,
                                               @Param("status") String status,
                                               Pageable pageable);
}
