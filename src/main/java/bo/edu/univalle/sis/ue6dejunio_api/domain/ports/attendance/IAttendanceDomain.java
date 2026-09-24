package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyStatusCount;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface IAttendanceDomain {
    boolean courseEnrollmentExists(UUID courseEnrollmentId);

    boolean courseExists(UUID courseId);

    UUID courseOfCourseEnrollment(UUID courseEnrollmentId);

    UUID courseOfClassGroup(UUID classGroupId);

    UUID teacherOfClassGroup(UUID classGroupId);

    /**
     * id_academic_year of the course, used to look up its Director-configured trimester periods.
     */
    Integer academicYearOfCourse(UUID courseId);

    Attendance upsertDaily(UUID courseEnrollmentId, LocalDate date, String status);

    Attendance upsertSession(
            UUID courseEnrollmentId, UUID classGroupId, LocalDate date, String status);

    List<Attendance> byCourseEnrollment(UUID courseEnrollmentId);

    List<Attendance> dailyByCourseEnrollment(UUID courseEnrollmentId);

    List<Attendance> dailyByCourseEnrollmentIn(Collection<UUID> courseEnrollmentIds);

    /**
     * Session rows (id_class_group = the given group) for a batch of enrollments, in one query.
     * Backs the per-subject sheet the technical teacher fills in.
     */
    List<Attendance> sessionByClassGroupAndCourseEnrollmentIn(
            UUID classGroupId, Collection<UUID> courseEnrollmentIds);

    /**
     * Single IN-query existence check for a batch of course enrollment ids. Returns only the subset
     * that actually exist, so the caller can report the first missing id without an existence query
     * per row.
     */
    Set<UUID> existingCourseEnrollmentIds(Collection<UUID> courseEnrollmentIds);

    /**
     * Subset of the given enrollments that actually belong to the given course, in one query. Lets
     * a session batch reject a foreign student without a course lookup per mark.
     */
    Set<UUID> courseEnrollmentIdsInCourse(Collection<UUID> courseEnrollmentIds, UUID courseId);

    /**
     * Batched upsert of daily (id_class_group NULL) attendance for the given date: loads any
     * existing rows for (courseEnrollmentId IN ..., date) in one query, then creates/updates and
     * saves all rows in a single batch — no per-mark find + save round trip.
     */
    List<Attendance> upsertDailyBatch(LocalDate date, Map<UUID, String> statusByCourseEnrollmentId);

    /** Same batched upsert as {@link #upsertDailyBatch}, but for one class group's session rows. */
    List<Attendance> upsertSessionBatch(
            UUID classGroupId, LocalDate date, Map<UUID, String> statusByCourseEnrollmentId);

    /**
     * One aggregated GROUP BY query: daily (id_class_group IS NULL) attendance counts per (date,
     * status) for all enrollments in the given course. No per-enrollment loop.
     */
    List<DailyStatusCount> dailyStatusCountsByCourseGroupedByDate(UUID courseId);
}
