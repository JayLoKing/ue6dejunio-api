package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CourseWithSubjects;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CreateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.UpdateCourseCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ICourseService {
    CourseWithSubjects create(CreateCourseCommand command);

    Course update(UUID id, UpdateCourseCommand command);

    Course setHomeroomTeacher(UUID id, UUID teacherId);

    /**
     * Trades the homeroom teacher of two courses, atomically: A gets B's teacher, B gets A's
     * teacher, and every class group each outgoing teacher held in their own course moves with them
     * into the course they arrive at.
     *
     * <p>For the case the ordinary reassignment path does not cover: two homeroom teachers, both
     * still active, trading places (or fixing a wrong assignment made at course creation). Nobody
     * is leaving the school, so the de-baja rule that guards {@link #setHomeroomTeacher} must not
     * apply here — both accounts are expected to stay active through the whole operation.
     *
     * <p>Both courses must exist, be different from each other, and each already have a homeroom
     * teacher — an empty seat would leave that course headless while its subjects stay on a teacher
     * who no longer belongs there. Both homeroom teachers must be active; an inactive one means
     * this is not a swap and belongs to {@link #setHomeroomTeacher} instead.
     *
     * @return the updated course A
     */
    Course swapHomeroomTeachers(UUID courseAId, UUID courseBId);

    Course getById(UUID id);

    /** The course a teacher is homeroom of, if any. Empty is an answer, not an error. */
    Optional<Course> homeroomCourseOf(UUID teacherId);

    PageResult<Course> list(Integer academicYearId, PageQuery pageQuery);

    /**
     * Every course of one gestión, read page by page rather than in one unbounded query.
     *
     * <p>What the whole-school reports iterate. They ask for the courses and not a page of them,
     * because a podium or a risk list built from the first page drops a classroom without saying
     * so; the paging stays here so the loop that makes it correct lives in one place.
     *
     * @param academicYearId the gestión, required in practice by every caller. Passing null asks
     *     for every year at once, which no report should want: the listing's order is only total
     *     within one year.
     */
    List<Course> allOfYear(Integer academicYearId);

    void delete(UUID id);
}
