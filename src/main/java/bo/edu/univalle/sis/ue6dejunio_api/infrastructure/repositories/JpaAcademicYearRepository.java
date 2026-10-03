package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicYearEntity;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAcademicYearRepository extends JpaRepository<AcademicYearEntity, Integer> {
    Optional<AcademicYearEntity> findByYear(Integer year);

    Optional<AcademicYearEntity> findByActiveTrue();

    boolean existsByActiveTrue();

    /**
     * Id of the gestión with the greatest year below the given one's. {@code MAX} over no rows is
     * NULL and {@code year = NULL} matches nothing, so a first year answers empty.
     */
    @Query(
            """
        SELECT y.id FROM AcademicYearEntity y
        WHERE y.year = (
            SELECT MAX(p.year) FROM AcademicYearEntity p
            WHERE p.year < (SELECT r.year FROM AcademicYearEntity r WHERE r.id = :yearId))
        """)
    Optional<Integer> previousYearId(@Param("yearId") Integer yearId);

    /** The same, starting from a course instead of a year id. */
    @Query(
            """
        SELECT y.id FROM AcademicYearEntity y
        WHERE y.year = (
            SELECT MAX(p.year) FROM AcademicYearEntity p
            WHERE p.year < (SELECT c.academicYear.year FROM CourseEntity c WHERE c.id = :courseId))
        """)
    Optional<Integer> previousYearIdOfCourse(@Param("courseId") UUID courseId);

    /**
     * Newest first: the gestión a school works in is the one it opens a picker to select, and every
     * year before it is history somebody scrolls back to.
     */
    List<AcademicYearEntity> findAllByOrderByYearDesc();

    /**
     * First half of a switch. The partial unique index on {@code active} is checked row by row, so
     * the old row has to be cleared in its own statement before the new one is set.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AcademicYearEntity y SET y.active = false WHERE y.active = true AND y.id <> :id")
    int deactivateAllExcept(@Param("id") Integer id);

    /** Second half of a switch; see {@link #deactivateAllExcept(Integer)}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AcademicYearEntity y SET y.active = true WHERE y.id = :id")
    int activate(@Param("id") Integer id);

    /** Closing also clears {@code active}: setting a flag to false never meets the unique index. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AcademicYearEntity y SET y.closedAt = :at, y.active = false WHERE y.id = :id")
    int close(@Param("id") Integer id, @Param("at") LocalDateTime at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AcademicYearEntity y SET y.closedAt = NULL WHERE y.id = :id")
    int reopen(@Param("id") Integer id);

    // The ones below answer "which closed years do these records sit in" for the closed-year
    // guard: one query each however many ids, joined up to the year, so a batch never asks per row.
    @Query(
            """
        SELECT DISTINCT y.year FROM ClassGroupEntity cg
        JOIN cg.course c JOIN c.academicYear y
        WHERE cg.id IN :ids AND y.closedAt IS NOT NULL
        """)
    List<Integer> closedYearsOfClassGroups(@Param("ids") Collection<UUID> ids);

    @Query(
            """
        SELECT DISTINCT y.year FROM CourseEnrollmentEntity ce
        JOIN ce.course c JOIN c.academicYear y
        WHERE ce.id IN :ids AND y.closedAt IS NOT NULL
        """)
    List<Integer> closedYearsOfEnrollments(@Param("ids") Collection<UUID> ids);

    @Query(
            """
        SELECT DISTINCT y.year FROM CourseEntity c
        JOIN c.academicYear y
        WHERE c.id IN :ids AND y.closedAt IS NOT NULL
        """)
    List<Integer> closedYearsOfCourses(@Param("ids") Collection<UUID> ids);

    @Query(
            """
        SELECT DISTINCT y.year FROM CurriculumPlanEntity p
        JOIN p.course c JOIN c.academicYear y
        WHERE p.id IN :ids AND y.closedAt IS NOT NULL
        """)
    List<Integer> closedYearsOfPlans(@Param("ids") Collection<UUID> ids);

    // A prediction carries its class group as a bare uuid, not an association, so the join to the
    // class group is spelled out.
    @Query(
            """
        SELECT DISTINCT y.year FROM RiskPredictionEntity rp, ClassGroupEntity cg
        JOIN cg.course c JOIN c.academicYear y
        WHERE rp.id IN :ids AND cg.id = rp.classGroupId AND y.closedAt IS NOT NULL
        """)
    List<Integer> closedYearsOfRiskPredictions(@Param("ids") Collection<UUID> ids);
}
