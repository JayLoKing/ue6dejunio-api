package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEnrollmentEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCourseEnrollmentRepository extends JpaRepository<CourseEnrollmentEntity, UUID> {

    /**
     * Student and status of every enrolment these students hold in the course. Two scalars rather
     * than the entities: the import only has to tell a seat still held from a closed row it must
     * reopen, and loading forty entities to read one column each would be the expensive way to ask.
     */
    @Query(
            """
        SELECT e.student.id, e.status FROM CourseEnrollmentEntity e
        WHERE e.course.id = :courseId AND e.student.id IN :studentIds
        """)
    List<Object[]> enrollmentStatuses(
            @Param("courseId") UUID courseId, @Param("studentIds") Collection<UUID> studentIds);

    /** (courseId, status) of every enrolment the student holds in one gestión, newest first. */
    @Query(
            """
        SELECT e.course.id, e.status FROM CourseEnrollmentEntity e
        WHERE e.student.id = :studentId AND e.course.academicYear.id = :yearId
        ORDER BY e.enrollmentDate DESC, e.id
        """)
    List<Object[]> seatsOfStudentInYear(
            @Param("studentId") UUID studentId, @Param("yearId") Integer yearId);

    /**
     * Reopens closed enrolments in bulk, so a readmission costs one statement for the whole import
     * rather than one per student who came back.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
        UPDATE CourseEnrollmentEntity e
        SET e.status = :status, e.enrollmentDate = :enrollmentDate
        WHERE e.course.id = :courseId AND e.student.id IN :studentIds
        """)
    int reactivateEnrollments(
            @Param("courseId") UUID courseId,
            @Param("studentIds") Collection<UUID> studentIds,
            @Param("status") String status,
            @Param("enrollmentDate") LocalDate enrollmentDate);

    /**
     * Closes every enrollment of a year in one statement. A withdrawn enrollment never holds a
     * result, so any it kept from an earlier close is cleared in the same pass.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
        UPDATE CourseEnrollmentEntity e
        SET e.closedAt = :at,
            e.finalAverage = CASE WHEN e.status = :withdrawn THEN NULL ELSE e.finalAverage END,
            e.finalResult = CASE WHEN e.status = :withdrawn THEN NULL ELSE e.finalResult END
        WHERE e.course.id IN (SELECT c.id FROM CourseEntity c WHERE c.academicYear.id = :yearId)
        """)
    int closeOfYear(
            @Param("yearId") Integer yearId,
            @Param("at") LocalDateTime at,
            @Param("withdrawn") String withdrawn);

    /**
     * Withdraws a student's effective enrollments in the years still open, in one statement. A
     * closed year keeps its history: neither the status nor the frozen result of an enrollment
     * there changes when the student leaves later.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
        UPDATE CourseEnrollmentEntity e SET e.status = :withdrawn
        WHERE e.student.id = :studentId AND e.status = :effective
        AND e.course.id IN (
            SELECT c.id FROM CourseEntity c WHERE c.academicYear.closedAt IS NULL)
        """)
    int withdrawEffectiveInOpenYears(
            @Param("studentId") UUID studentId,
            @Param("effective") String effective,
            @Param("withdrawn") String withdrawn);

    /** Reopens the enrollments of a year; the frozen average and result stay. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
        UPDATE CourseEnrollmentEntity e SET e.closedAt = NULL
        WHERE e.course.id IN (SELECT c.id FROM CourseEntity c WHERE c.academicYear.id = :yearId)
        """)
    int reopenOfYear(@Param("yearId") Integer yearId);

    /**
     * (enrollmentId, finalAverage, finalResult, year closedAt) of the given enrollments, as raw
     * rows. Scalars only: no entity, no student, no course is hydrated to read four columns.
     */
    @Query(
            """
        SELECT e.id, e.finalAverage, e.finalResult, y.closedAt
        FROM CourseEnrollmentEntity e
        JOIN e.course c
        JOIN c.academicYear y
        WHERE e.id IN :ids
        """)
    List<Object[]> outcomesOf(@Param("ids") Collection<UUID> ids);

    @EntityGraph(attributePaths = "student")
    Page<CourseEnrollmentEntity> findByCourse_Id(UUID courseId, Pageable pageable);

    @EntityGraph(attributePaths = "student")
    Page<CourseEnrollmentEntity> findByCourse_IdAndStatus(
            UUID courseId, String status, Pageable pageable);

    /** Ids of the given enrollments that exist, without hydrating the entities behind them. */
    @Query("SELECT ce.id FROM CourseEnrollmentEntity ce WHERE ce.id IN :courseEnrollmentIds")
    List<UUID> findExistingIds(@Param("courseEnrollmentIds") Collection<UUID> courseEnrollmentIds);

    /**
     * (enrollmentId, courseId) pairs for the given enrollments, as raw rows: the tuple-to-map
     * conversion belongs in the adapter, not in a domain type.
     */
    @Query(
            "SELECT ce.id, ce.course.id FROM CourseEnrollmentEntity ce WHERE ce.id IN :courseEnrollmentIds")
    List<Object[]> findCourseIdsByEnrollment(
            @Param("courseEnrollmentIds") Collection<UUID> courseEnrollmentIds);

    @Query("SELECT ce.course.id FROM CourseEnrollmentEntity ce WHERE ce.student.id = :studentId")
    List<UUID> findCourseIdsOfStudent(@Param("studentId") UUID studentId);

    /**
     * (courseId, enrolments) for a whole gestión, as raw rows.
     *
     * <p>One grouped count instead of a count per classroom. The Director's tables need the size of
     * every room at once, and asking room by room is the round trip per course those tables exist
     * to remove.
     */
    @Query(
            "SELECT ce.course.id, COUNT(ce) FROM CourseEnrollmentEntity ce "
                    + "WHERE ce.course.academicYear.id = :academicYearId GROUP BY ce.course.id")
    List<Object[]> countByCourseOfAcademicYear(@Param("academicYearId") Integer academicYearId);

    /** Ids of the given enrollments that belong to the given course, in a single query. */
    @Query(
            "SELECT ce.id FROM CourseEnrollmentEntity ce "
                    + "WHERE ce.id IN :courseEnrollmentIds AND ce.course.id = :courseId")
    List<UUID> findIdsInCourse(
            @Param("courseEnrollmentIds") Collection<UUID> courseEnrollmentIds,
            @Param("courseId") UUID courseId);

    /**
     * (gender, enrolments) for the active enrolments of one course, as raw rows.
     *
     * <p>Only the status {@code
     * bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters.CourseEnrollmentRepositoryAdapter#activeStudentsByCourse}
     * also uses: a withdrawn student is off the roll and does not belong in either count. That is
     * why these two numbers disagree with {@code CourseOverview.students().totalElements()} on a
     * course somebody left — the roster total intentionally keeps every enrolment, active or not,
     * because the marks earned before a withdrawal are still owed to the year's records (see {@code
     * GradebookService#centralizer}, which reads {@code #findByCourse_Id} rather than the
     * active-only listing). The head count that does agree with these is {@link
     * #countByCourse_IdAndStatus} below.
     */
    @Query(
            "SELECT e.student.gender, COUNT(e) FROM CourseEnrollmentEntity e "
                    + "WHERE e.course.id = :courseId AND e.status = :status "
                    + "GROUP BY e.student.gender")
    List<Object[]> countByCourseAndStatusGroupedByGender(
            @Param("courseId") UUID courseId, @Param("status") String status);

    /**
     * The same population as the grouped count above, counted whole.
     *
     * <p>Not the sum of that map: a student whose gender was never recorded groups under null and
     * is dropped, so summing would lose them. A derived query rather than a {@code @Query} because
     * it is exactly what the method name says.
     */
    long countByCourse_IdAndStatus(UUID courseId, String status);

    /**
     * The homeroom teachers of every course the student sat in, active accounts only.
     *
     * <p>Enrolments of any status: a withdrawal is what closes them, so asking for the active ones
     * afterwards would find nobody left to tell.
     */
    @Query(
            "SELECT DISTINCT t.id FROM CourseEnrollmentEntity ce "
                    + "JOIN ce.course c JOIN c.homeroomTeacher t "
                    + "WHERE ce.student.id = :studentId AND t.active = true")
    List<UUID> findHomeroomTeacherIdsOfStudent(@Param("studentId") UUID studentId);

    /**
     * The teachers of the active class groups in those same courses — the technical teachers, who
     * keep a roster of their own for the subject they run.
     */
    @Query(
            "SELECT DISTINCT t.id FROM CourseEnrollmentEntity ce "
                    + "JOIN ce.course c, ClassGroupEntity cg JOIN cg.teacher t "
                    + "WHERE cg.course = c AND cg.active = true "
                    + "AND ce.student.id = :studentId AND t.active = true")
    List<UUID> findClassGroupTeacherIdsOfStudent(@Param("studentId") UUID studentId);
}
