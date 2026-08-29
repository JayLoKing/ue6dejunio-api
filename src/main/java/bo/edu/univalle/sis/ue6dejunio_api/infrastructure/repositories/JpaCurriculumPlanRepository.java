package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface JpaCurriculumPlanRepository extends JpaRepository<CurriculumPlanEntity, UUID> {

    boolean existsByCourse_IdAndTrimesterAndPlanNumber(UUID courseId, Integer trimester, Integer planNumber);

    boolean existsBySourcePlan_Id(UUID sourcePlanId);

    /** Which of the given courses already hold that numbered month, in one query. */
    @Query("""
        SELECT p.course.id FROM CurriculumPlanEntity p
        WHERE p.course.id IN :courseIds AND p.trimester = :trimester AND p.planNumber = :planNumber
        """)
    Set<UUID> courseIdsWithPlan(@Param("courseIds") List<UUID> courseIds,
                                @Param("trimester") Integer trimester,
                                @Param("planNumber") Integer planNumber);

    /**
     * A plan with its subject blocks joined, but not their weekly rows.
     *
     * <p>Both collections are ordered lists, and Hibernate refuses to join-fetch two of them in one
     * query — it throws {@code MultipleBagFetchException} rather than return a wrong cartesian
     * product. The rows therefore come from a second query, {@link
     * JpaCurriculumPlanSubjectRepository#fetchEntriesOfPlan}, whose results land in the same
     * persistence context. Two ordered queries, no fan-out per subject.
     */
    @Query("SELECT p FROM CurriculumPlanEntity p WHERE p.id = :id")
    @EntityGraph(attributePaths = {
        "course", "course.grade", "course.grade.level", "course.parallel",
        "course.homeroomTeacher",
        "subjects", "subjects.classGroup", "subjects.classGroup.subject",
        "subjects.classGroup.subject.area", "subjects.classGroup.teacher",
        "createdBy", "updatedBy"})
    Optional<CurriculumPlanEntity> findWithContent(@Param("id") UUID id);

    /**
     * The status by itself, for guards that ask whether the plan can still be written. Reading the
     * whole document for one string doubles the queries of every write that follows it.
     */
    @Query("SELECT p.status FROM CurriculumPlanEntity p WHERE p.id = :id")
    Optional<String> findStatusById(@Param("id") UUID id);

    /** Who may write this plan: the homeroom teacher plus the teacher of each subject block. */
    @Query("""
        SELECT ht.id FROM CurriculumPlanEntity p JOIN p.course c JOIN c.homeroomTeacher ht
        WHERE p.id = :planId
        UNION
        SELECT t.id FROM CurriculumPlanSubjectEntity s JOIN s.classGroup cg JOIN cg.teacher t
        WHERE s.curriculumPlan.id = :planId
        """)
    Set<UUID> writerIdsOf(@Param("planId") UUID planId);

    /** Who may write one block: the homeroom teacher, or the teacher of that block's subject. */
    @Query("""
        SELECT ht.id FROM CurriculumPlanEntity p JOIN p.course c JOIN c.homeroomTeacher ht
        WHERE p.id = :planId
        UNION
        SELECT t.id FROM CurriculumPlanSubjectEntity s JOIN s.classGroup cg JOIN cg.teacher t
        WHERE s.curriculumPlan.id = :planId AND s.id = :planSubjectId
        """)
    Set<UUID> subjectWriterIdsOf(@Param("planId") UUID planId,
                                 @Param("planSubjectId") UUID planSubjectId);

    /**
     * Who owns the plan as a document: whoever opened it, plus the homeroom teacher of its course.
     * A specialist's own single-subject plan is administered by the specialist who opened it; a
     * homeroom teacher's course-wide plan is not administered by the specialists inside it.
     */
    @Query("""
        SELECT cb.id FROM CurriculumPlanEntity p JOIN p.createdBy cb WHERE p.id = :planId
        UNION
        SELECT ht.id FROM CurriculumPlanEntity p JOIN p.course c JOIN c.homeroomTeacher ht
        WHERE p.id = :planId
        """)
    Set<UUID> administratorIdsOf(@Param("planId") UUID planId);

    /**
     * A teacher sees a plan when they run the course or teach one of its subjects. The EXISTS is
     * what keeps a specialist's own plans visible without widening the listing to the school.
     */
    // The joins are explicit and outer on purpose. Writing p.course.homeroomTeacher.id in the WHERE
    // clause makes Hibernate render an inner join over the whole query, so a course with no
    // homeroom teacher assigned would drop every one of its plans from the listing — including
    // from the specialist who owns a block in them and matches through the EXISTS.
    @Query("""
        SELECT p FROM CurriculumPlanEntity p
        LEFT JOIN p.course c
        LEFT JOIN c.homeroomTeacher ht
        WHERE (:courseId IS NULL OR c.id = :courseId)
              AND (:trimester IS NULL OR p.trimester = :trimester)
              AND (:status IS NULL OR p.status = :status)
              AND (:teacherId IS NULL
                   OR ht.id = :teacherId
                   OR EXISTS (SELECT 1 FROM CurriculumPlanSubjectEntity s
                              WHERE s.curriculumPlan = p AND s.classGroup.teacher.id = :teacherId))
        """)
    @EntityGraph(attributePaths = {
        "course", "course.grade", "course.grade.level", "course.parallel",
        "course.homeroomTeacher", "createdBy", "updatedBy"})
    Page<CurriculumPlanEntity> search(@Param("courseId") UUID courseId,
                                      @Param("trimester") Integer trimester,
                                      @Param("status") String status,
                                      @Param("teacherId") UUID teacherId,
                                      Pageable pageable);
}
