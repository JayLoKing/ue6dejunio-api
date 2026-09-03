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

    @Query(value = """
        SELECT DISTINCT new bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem(
            s.id, s.rudeCode, CONCAT(s.names, ' ', s.lastNames), g.name, p.name, l.name)
        FROM StudentEntity s
        LEFT JOIN CourseEnrollmentEntity ce ON ce.student = s AND ce.status = 'Effective'
        LEFT JOIN ce.course c
        LEFT JOIN c.grade g
        LEFT JOIN c.parallel p
        LEFT JOIN g.level l
        WHERE s.status = 'Effective' AND (:courseId IS NULL OR c.id = :courseId)
        """,
        countQuery = """
        SELECT COUNT(DISTINCT s) FROM StudentEntity s
        LEFT JOIN CourseEnrollmentEntity ce ON ce.student = s AND ce.status = 'Effective'
        LEFT JOIN ce.course c
        WHERE s.status = 'Effective' AND (:courseId IS NULL OR c.id = :courseId)
        """)
    Page<StudentDirectoryItem> listDirectory(@Param("courseId") UUID courseId, Pageable pageable);

    @Query(value = """
        SELECT DISTINCT new bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem(
            s.id, s.rudeCode, CONCAT(s.names, ' ', s.lastNames), g.name, p.name, l.name)
        FROM StudentEntity s
        LEFT JOIN CourseEnrollmentEntity ce ON ce.student = s AND ce.status = 'Effective'
        LEFT JOIN ce.course c
        LEFT JOIN c.grade g
        LEFT JOIN c.parallel p
        LEFT JOIN g.level l
        WHERE s.status = 'Effective' AND (:courseId IS NULL OR c.id = :courseId)
              AND (LOWER(s.names) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.lastNames) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.rudeCode) LIKE LOWER(CONCAT('%', :q, '%')))
        """,
        countQuery = """
        SELECT COUNT(DISTINCT s) FROM StudentEntity s
        LEFT JOIN CourseEnrollmentEntity ce ON ce.student = s AND ce.status = 'Effective'
        LEFT JOIN ce.course c
        WHERE s.status = 'Effective' AND (:courseId IS NULL OR c.id = :courseId)
              AND (LOWER(s.names) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.lastNames) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.rudeCode) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<StudentDirectoryItem> searchDirectory(@Param("q") String q, @Param("courseId") UUID courseId, Pageable pageable);
}
