package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import java.util.Collection;
import java.util.List;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * One status change written across many students at once.
     *
     * <p>Bulk JPQL goes around dirty tracking, so every column the single-student path stamps has
     * to be named here — {@code statusChangedAt} above all, which the adapter otherwise sets on the
     * entity itself.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE StudentEntity s
        SET s.status = :status, s.statusReason = :reason, s.statusNote = :note,
            s.statusChangedAt = :changedAt, s.statusChangedBy = :changedBy
        WHERE s.id IN :ids
        """)
    int updateStatusIn(@Param("ids") Collection<UUID> ids,
                       @Param("status") String status,
                       @Param("reason") String reason,
                       @Param("note") String note,
                       @Param("changedAt") LocalDateTime changedAt,
                       @Param("changedBy") UserEntity changedBy);

    /*
     * The two directory queries below repeat their FROM and their filters on purpose: the only
     * difference is the name match, and it stays out of the listing one because binding a null
     * into a LIKE is what the adapter's blank-q split exists to avoid.
     *
     * The enrolment is joined on `ce.status = s.status` rather than on 'Effective'. A withdrawal
     * closes the student's enrolments in the same transaction that closes the student, so this
     * reads as "the enrolment that matches what the student now is" — and it is what lets the
     * Director filter withdrawn students by the grade they were in. Joined on 'Effective' alone,
     * every withdrawn row came back with no course at all and the grade filter excluded them all.
     *
     * The gestión is what keeps a student who moved up to one row. They hold an enrolment per year,
     * all of them of the same status, so without this predicate the same person came back once per
     * enrolment while COUNT(DISTINCT s) counted them once and the page disagreed with its own
     * total. The application supplies the current year when the caller names neither a year nor a
     * course.
     *
     * `y.id IS NULL` is the escape for a student who is registered but not yet enrolled: they
     * belong to no gestión, and without it every year filter — including the default one — would
     * drop the very students the secretariat still has work to do on.
     *
     * Narrower limit, still open: two enrolments of the same status inside ONE year — a mid-year
     * course change — are still two content rows against a count of one. The year closes the
     * duplication across gestiones, not this one, which needs a rule about which enrolment of a
     * year the directory means.
     */

    @Query(value = """
        SELECT DISTINCT new bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem(
            s.id, s.rudeCode, s.identityCard, CONCAT(s.names, ' ', s.lastNames),
            g.name, p.name, l.name, s.status, y.year)
        FROM StudentEntity s
        LEFT JOIN CourseEnrollmentEntity ce ON ce.student = s AND ce.status = s.status
        LEFT JOIN ce.course c
        LEFT JOIN c.grade g
        LEFT JOIN c.parallel p
        LEFT JOIN c.academicYear y
        LEFT JOIN g.level l
        WHERE (:status IS NULL OR s.status = :status)
              AND (:courseId IS NULL OR c.id = :courseId)
              AND (:gradeId IS NULL OR g.id = :gradeId)
              AND (:parallelId IS NULL OR p.id = :parallelId)
              AND (:academicYearId IS NULL OR y.id = :academicYearId OR y.id IS NULL)
        """,
        countQuery = """
        SELECT COUNT(DISTINCT s) FROM StudentEntity s
        LEFT JOIN CourseEnrollmentEntity ce ON ce.student = s AND ce.status = s.status
        LEFT JOIN ce.course c
        LEFT JOIN c.grade g
        LEFT JOIN c.parallel p
        LEFT JOIN c.academicYear y
        WHERE (:status IS NULL OR s.status = :status)
              AND (:courseId IS NULL OR c.id = :courseId)
              AND (:gradeId IS NULL OR g.id = :gradeId)
              AND (:parallelId IS NULL OR p.id = :parallelId)
              AND (:academicYearId IS NULL OR y.id = :academicYearId OR y.id IS NULL)
        """)
    Page<StudentDirectoryItem> listDirectory(@Param("courseId") UUID courseId,
                                             @Param("gradeId") Integer gradeId,
                                             @Param("parallelId") Integer parallelId,
                                             @Param("academicYearId") Integer academicYearId,
                                             @Param("status") String status,
                                             Pageable pageable);

    @Query(value = """
        SELECT DISTINCT new bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem(
            s.id, s.rudeCode, s.identityCard, CONCAT(s.names, ' ', s.lastNames),
            g.name, p.name, l.name, s.status, y.year)
        FROM StudentEntity s
        LEFT JOIN CourseEnrollmentEntity ce ON ce.student = s AND ce.status = s.status
        LEFT JOIN ce.course c
        LEFT JOIN c.grade g
        LEFT JOIN c.parallel p
        LEFT JOIN c.academicYear y
        LEFT JOIN g.level l
        WHERE (:status IS NULL OR s.status = :status)
              AND (:courseId IS NULL OR c.id = :courseId)
              AND (:gradeId IS NULL OR g.id = :gradeId)
              AND (:parallelId IS NULL OR p.id = :parallelId)
              AND (:academicYearId IS NULL OR y.id = :academicYearId OR y.id IS NULL)
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
        LEFT JOIN c.academicYear y
        WHERE (:status IS NULL OR s.status = :status)
              AND (:courseId IS NULL OR c.id = :courseId)
              AND (:gradeId IS NULL OR g.id = :gradeId)
              AND (:parallelId IS NULL OR p.id = :parallelId)
              AND (:academicYearId IS NULL OR y.id = :academicYearId OR y.id IS NULL)
              AND (LOWER(s.names) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.lastNames) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.rudeCode) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.identityCard) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<StudentDirectoryItem> searchDirectory(@Param("q") String q,
                                               @Param("courseId") UUID courseId,
                                               @Param("gradeId") Integer gradeId,
                                               @Param("parallelId") Integer parallelId,
                                               @Param("academicYearId") Integer academicYearId,
                                               @Param("status") String status,
                                               Pageable pageable);
}
