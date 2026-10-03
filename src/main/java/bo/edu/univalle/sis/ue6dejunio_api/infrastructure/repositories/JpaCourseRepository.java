package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCourseRepository extends JpaRepository<CourseEntity, UUID> {
    boolean existsByGrade_IdAndParallel_IdAndAcademicYear_Id(
            Integer gradeId, Integer parallelId, Integer yearId);

    /** The open courses of a gestión, with the to-ones the copy names its skipped ones by. */
    @Query(
            """
        SELECT c FROM CourseEntity c
        WHERE c.academicYear.id = :yearId AND c.active = true
        ORDER BY c.grade.id, c.parallel.id
        """)
    @EntityGraph(attributePaths = {"grade", "parallel", "academicYear", "homeroomTeacher"})
    List<CourseEntity> findActiveOfYear(@Param("yearId") Integer yearId);

    /** Only the two ids, so asking what a gestión already holds loads no entity at all. */
    @Query(
            """
        SELECT c.grade.id AS gradeId, c.parallel.id AS parallelId FROM CourseEntity c
        WHERE c.academicYear.id = :yearId
        """)
    List<GradeParallelRow> findGradeParallelsOfYear(@Param("yearId") Integer yearId);

    /** A grade and a parallel, as the query above hands them back. */
    interface GradeParallelRow {
        Integer getGradeId();

        Integer getParallelId();
    }

    /** Any enrolment of the course, whatever its status. */
    @Query(
            value =
                    "SELECT EXISTS (SELECT 1 FROM course_enrollments e WHERE e.id_course ="
                            + " :courseId)",
            nativeQuery = true)
    boolean hasEnrollments(@Param("courseId") UUID courseId);

    /**
     * Teacher-authored content on the course or on any of its class groups, in one statement.
     * Scores and attendance cannot exist without an enrolment, but they are checked anyway so the
     * answer does not depend on that invariant.
     */
    @Query(
            value =
                    """
        SELECT EXISTS (SELECT 1 FROM curriculum_plans p WHERE p.id_course = :courseId)
            OR EXISTS (SELECT 1 FROM pedagogical_reports r WHERE r.id_course = :courseId)
            OR EXISTS (SELECT 1 FROM class_groups g
                       JOIN evaluation_criteria ec ON ec.id_class_group = g.id_class_group
                       WHERE g.id_course = :courseId)
            OR EXISTS (SELECT 1 FROM class_groups g
                       JOIN academic_scores s ON s.id_class_group = g.id_class_group
                       WHERE g.id_course = :courseId)
            OR EXISTS (SELECT 1 FROM class_groups g
                       JOIN attendance a ON a.id_class_group = g.id_class_group
                       WHERE g.id_course = :courseId)
        """,
            nativeQuery = true)
    boolean hasAcademicRecords(@Param("courseId") UUID courseId);

    boolean existsByGrade_Id(Integer gradeId);

    boolean existsByParallel_Id(Integer parallelId);

    @Query(
            """
        SELECT c FROM CourseEntity c
        WHERE (:yearId IS NULL OR c.academicYear.id = :yearId)
        ORDER BY c.grade.id, c.parallel.id
        """)
    @EntityGraph(attributePaths = {"grade", "parallel", "academicYear", "homeroomTeacher"})
    Page<CourseEntity> search(@Param("yearId") Integer yearId, Pageable pageable);

    Optional<CourseEntity>
            findFirstByHomeroomTeacher_IdAndActiveTrueOrderByAcademicYear_ActiveDescAcademicYear_YearDesc(
                    UUID teacherId);

    Optional<CourseEntity> findFirstByHomeroomTeacher_IdAndAcademicYear_IdAndActiveTrue(
            UUID teacherId, Integer yearId);

    /**
     * The courses a rotation copies into, read with the to-ones the printed heading names. They are
     * EAGER, so {@code findAllById} would resolve each of them with a select of its own.
     */
    @EntityGraph(attributePaths = {"grade", "parallel", "academicYear", "homeroomTeacher"})
    List<CourseEntity> findByIdIn(Collection<UUID> ids);

    boolean existsByHomeroomTeacher_IdAndIdIn(UUID teacherId, Collection<UUID> courseIds);

    long countByHomeroomTeacher_IdAndIdIn(UUID teacherId, Collection<UUID> courseIds);

    /**
     * The other parallels of the same grade and year — 3ro "B" and "C" when asked about 3ro "A".
     * These are the courses a month's plan is copied into when the grade's teachers take turns.
     */
    @Query(
            """
        SELECT sibling.id FROM CourseEntity sibling, CourseEntity origin
        WHERE origin.id = :courseId
              AND sibling.grade.id = origin.grade.id
              AND sibling.academicYear.id = origin.academicYear.id
              AND sibling.id <> origin.id
              AND sibling.active = true
        ORDER BY sibling.parallel.id
        """)
    List<UUID> findSiblingCourseIds(@Param("courseId") UUID courseId);
}
