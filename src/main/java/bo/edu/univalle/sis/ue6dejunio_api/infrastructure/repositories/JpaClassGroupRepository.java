package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ClassGroupEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaClassGroupRepository extends JpaRepository<ClassGroupEntity, UUID> {
    boolean existsByCourse_IdAndSubject_Id(UUID courseId, UUID subjectId);

    boolean existsByCourse_IdAndTeacher_Id(UUID courseId, UUID teacherId);

    boolean existsByTeacher_IdAndCourse_IdIn(UUID teacherId, Collection<UUID> courseIds);
    // The to-one associations below are EAGER on the entity, and a derived query does not
    // join-fetch those — Hibernate would resolve subject and teacher with a follow-up select per
    // row. The graph turns the listing back into a single query.
    @EntityGraph(attributePaths = {"course", "course.grade", "course.parallel", "subject", "subject.area", "teacher"})
    List<ClassGroupEntity> findByCourse_IdOrderBySubject_Name(UUID courseId);

    @EntityGraph(attributePaths = {"course", "course.grade", "course.parallel", "subject", "subject.area", "teacher"})
    List<ClassGroupEntity> findByTeacher_IdOrderBySubject_Name(UUID teacherId);

    /** The groups a plan opens its blocks over, read with the associations the block heading names. */
    @EntityGraph(attributePaths = {"course", "subject", "subject.area", "teacher"})
    List<ClassGroupEntity> findByIdIn(Collection<UUID> ids);

    /**
     * The active class groups of a course, ordered the way the curriculum plan prints them: by
     * knowledge area first, then by subject. A plan opened over this list comes out already in the
     * order of the paper form.
     */
    @Query("""
        SELECT cg FROM ClassGroupEntity cg
        WHERE cg.course.id = :courseId AND cg.active = true
        ORDER BY cg.subject.area.displayOrder, cg.subject.name
        """)
    @EntityGraph(attributePaths = {"subject", "subject.area", "teacher"})
    List<ClassGroupEntity> findActiveOfCourseInPlanOrder(@Param("courseId") UUID courseId);

    long countByCourse_IdAndIdInAndActiveTrue(UUID courseId, Collection<UUID> ids);

    /**
     * The same listing for several courses at once, so copying a plan into the parallels reads
     * their subjects in one query instead of one per course.
     */
    @Query("""
        SELECT cg FROM ClassGroupEntity cg
        WHERE cg.course.id IN :courseIds AND cg.active = true
        ORDER BY cg.subject.area.displayOrder, cg.subject.name
        """)
    @EntityGraph(attributePaths = {"course", "subject", "subject.area", "teacher"})
    List<ClassGroupEntity> findActiveOfCoursesInPlanOrder(@Param("courseIds") Collection<UUID> courseIds);
}
