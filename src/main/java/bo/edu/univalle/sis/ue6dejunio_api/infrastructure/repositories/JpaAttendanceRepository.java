package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AttendanceEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAttendanceRepository extends JpaRepository<AttendanceEntity, UUID> {
    Optional<AttendanceEntity> findByCourseEnrollment_IdAndDateAndClassGroupIsNull(
            UUID courseEnrollmentId, LocalDate date);

    Optional<AttendanceEntity> findByCourseEnrollment_IdAndClassGroup_IdAndDate(
            UUID courseEnrollmentId, UUID classGroupId, LocalDate date);

    List<AttendanceEntity> findByCourseEnrollment_IdOrderByDate(UUID courseEnrollmentId);

    List<AttendanceEntity> findByCourseEnrollment_IdAndClassGroupIsNullOrderByDate(
            UUID courseEnrollmentId);

    List<AttendanceEntity> findByCourseEnrollment_IdInAndClassGroupIsNullOrderByDate(
            Collection<UUID> courseEnrollmentIds);

    List<AttendanceEntity> findByCourseEnrollment_IdInAndClassGroup_IdOrderByDate(
            Collection<UUID> courseEnrollmentIds, UUID classGroupId);

    /**
     * Existing session rows for a batch of enrollments on one date and one class group, in a single
     * query. Mirrors the daily variant used by the batch upsert.
     */
    List<AttendanceEntity> findByCourseEnrollment_IdInAndDateAndClassGroup_Id(
            Collection<UUID> courseEnrollmentIds, LocalDate date, UUID classGroupId);

    /**
     * Existing daily (id_class_group IS NULL) rows for a batch of enrollments on one date, in a
     * single query. Used to build a batch upsert set without a per-enrollment find.
     */
    List<AttendanceEntity> findByCourseEnrollment_IdInAndDateAndClassGroupIsNull(
            Collection<UUID> courseEnrollmentIds, LocalDate date);

    /**
     * Single GROUP BY query: daily-only (id_class_group IS NULL) attendance counts per (date,
     * status) for every enrollment belonging to the given course. Returned as raw Object[] rows on
     * purpose: numeric/driver-specific conversion (e.g. COUNT() as Long) belongs in the
     * infrastructure adapter, not in the domain model.
     */
    @Query(
            """
        SELECT a.date, a.status, COUNT(a)
        FROM AttendanceEntity a
        WHERE a.courseEnrollment.course.id = :courseId AND a.classGroup IS NULL
        GROUP BY a.date, a.status
        """)
    List<Object[]> dailyStatusCountsByCourseGroupedByDate(@Param("courseId") UUID courseId);
}
