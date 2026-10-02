package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import java.util.Collection;
import java.util.Optional;
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
}
