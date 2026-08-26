package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEnrollmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaCourseEnrollmentRepository extends JpaRepository<CourseEnrollmentEntity, UUID> {

    @Query("""
        SELECT e.student.id FROM CourseEnrollmentEntity e
        WHERE e.course.id = :courseId AND e.student.id IN :studentIds
        """)
    List<UUID> enrolledStudentIds(@Param("courseId") UUID courseId,
                                            @Param("studentIds") Collection<UUID> studentIds);
    @EntityGraph(attributePaths = "student")
    Page<CourseEnrollmentEntity> findByCourse_Id(UUID courseId, Pageable pageable);

    @EntityGraph(attributePaths = "student")
    Page<CourseEnrollmentEntity> findByCourse_IdAndStatus(UUID courseId, String status, Pageable pageable);

    /** Ids of the given enrollments that exist, without hydrating the entities behind them. */
    @Query("SELECT ce.id FROM CourseEnrollmentEntity ce WHERE ce.id IN :courseEnrollmentIds")
    List<UUID> findExistingIds(@Param("courseEnrollmentIds") Collection<UUID> courseEnrollmentIds);

    /** (enrollmentId, courseId) pairs for the given enrollments, as raw rows: the tuple-to-map
     * conversion belongs in the adapter, not in a domain type. */
    @Query("SELECT ce.id, ce.course.id FROM CourseEnrollmentEntity ce WHERE ce.id IN :courseEnrollmentIds")
    List<Object[]> findCourseIdsByEnrollment(
        @Param("courseEnrollmentIds") Collection<UUID> courseEnrollmentIds);

    @Query("SELECT ce.course.id FROM CourseEnrollmentEntity ce WHERE ce.student.id = :studentId")
    List<UUID> findCourseIdsOfStudent(@Param("studentId") UUID studentId);

    /** Ids of the given enrollments that belong to the given course, in a single query. */
    @Query("SELECT ce.id FROM CourseEnrollmentEntity ce "
        + "WHERE ce.id IN :courseEnrollmentIds AND ce.course.id = :courseId")
    List<UUID> findIdsInCourse(@Param("courseEnrollmentIds") Collection<UUID> courseEnrollmentIds,
                               @Param("courseId") UUID courseId);
    List<CourseEnrollmentEntity> findByStudent_IdAndStatus(UUID studentId, String status);
}
