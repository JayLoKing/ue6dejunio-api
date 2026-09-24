package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEnrollmentEntity;
import java.time.LocalDate;
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

    /** Ids of the given enrollments that belong to the given course, in a single query. */
    @Query(
            "SELECT ce.id FROM CourseEnrollmentEntity ce "
                    + "WHERE ce.id IN :courseEnrollmentIds AND ce.course.id = :courseId")
    List<UUID> findIdsInCourse(
            @Param("courseEnrollmentIds") Collection<UUID> courseEnrollmentIds,
            @Param("courseId") UUID courseId);

    List<CourseEnrollmentEntity> findByStudent_IdAndStatus(UUID studentId, String status);

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
