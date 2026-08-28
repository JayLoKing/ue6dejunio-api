package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaCourseRepository extends JpaRepository<CourseEntity, UUID> {
    boolean existsByGrade_IdAndParallel_IdAndAcademicYear_Id(Integer gradeId, Integer parallelId, Integer yearId);
    boolean existsByGrade_Id(Integer gradeId);
    boolean existsByParallel_Id(Integer parallelId);

    @Query("""
        SELECT c FROM CourseEntity c
        WHERE (:yearId IS NULL OR c.academicYear.id = :yearId)
        ORDER BY c.grade.id, c.parallel.id
        """)
    @EntityGraph(attributePaths = {"grade", "parallel", "academicYear", "homeroomTeacher"})
    Page<CourseEntity> search(@Param("yearId") Integer yearId, Pageable pageable);

    Optional<CourseEntity> findFirstByHomeroomTeacher_IdAndActiveTrueOrderByAcademicYear_YearDesc(UUID teacherId);

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
    @Query("""
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
