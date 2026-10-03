package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.GradeParallel;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ICourseDomain {
    boolean gradeExists(Integer gradeId);

    boolean parallelExists(Integer parallelId);

    boolean userIsNonTechnicalTeacher(UUID userId);

    boolean existsByGradeParallelYear(Integer gradeId, Integer parallelId, Integer academicYearId);

    Course create(
            Integer gradeId, Integer parallelId, Integer academicYearId, UUID homeroomTeacherId);

    Course setHomeroomTeacher(UUID courseId, UUID teacherId);

    Course setActive(UUID courseId, boolean active);

    /** Whether the course has at least one enrolment, whatever its status. */
    boolean hasEnrollments(UUID courseId);

    /**
     * Whether teacher-authored content hangs off the course or its class groups: criteria,
     * curriculum plans, pedagogical reports, scores or attendance. A bare class group is not
     * content, so a freshly duplicated course carries none. One existence query, no rows loaded.
     */
    boolean hasAcademicRecords(UUID courseId);

    Optional<Course> findById(UUID id);

    /**
     * Whether the teacher is the homeroom teacher of at least one of the given courses. Resolved in
     * a single query so an ownership guard costs the same no matter how many courses it spans.
     */
    boolean isHomeroomTeacherOfAny(UUID teacherId, Collection<UUID> courseIds);

    /**
     * Whether the teacher is the homeroom teacher of every one of the given courses. An unknown
     * course counts as not owned, so a batch spanning a stale id is refused as a whole.
     */
    boolean isHomeroomTeacherOfAll(UUID teacherId, Collection<UUID> courseIds);

    PageResult<Course> list(Integer academicYearId, PageQuery pageQuery);

    /**
     * The active course the teacher runs as homeroom, whichever gestión it belongs to. For the
     * readers that ask "which is this teacher's classroom": when the same person holds one in two
     * gestiones, the active gestión's wins, then the newest.
     */
    Optional<Course> homeroomCourseOf(UUID teacherId);

    /**
     * The active course the teacher runs as homeroom in this one gestión. What decides whether a
     * second assignment is refused: a teacher is homeroom of one classroom per year, not for life.
     */
    Optional<Course> homeroomCourseOf(UUID teacherId, Integer academicYearId);

    /**
     * The open classrooms of a gestión, in one query: the ones a copy into another year starts
     * from. Closed courses are logical deletes and do not travel.
     */
    List<Course> activeOfYear(Integer academicYearId);

    /**
     * Every grade and parallel that already has a course in the gestión, closed ones included: the
     * pair is unique per year whatever the course's state, so a copy must not meet it twice.
     */
    Set<GradeParallel> gradeParallelsOfYear(Integer academicYearId);

    /**
     * Opens one active course per pair in the gestión, with no homeroom teacher, and says which id
     * each pair got. Reads nothing per row: the copy is written, not looked up.
     */
    Map<GradeParallel, UUID> createWithoutHomeroom(
            Integer academicYearId, Collection<GradeParallel> pairs);
}
