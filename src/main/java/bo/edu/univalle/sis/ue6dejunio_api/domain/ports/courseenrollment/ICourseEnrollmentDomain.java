package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ICourseEnrollmentDomain {
    boolean courseExists(UUID courseId);

    /**
     * The enrolment each of these students holds in the course and the status it is in, asked once
     * for the whole set.
     *
     * <p>Every status, not just the ones in force, and that is the point: an absent student has no
     * row and needs one, while a student with a closed row cannot be given a second — {@code
     * (id_student, id_course)} is unique — and has to come back through the row that recorded them
     * leaving. Answering both questions here is what keeps an import of forty at a fixed number of
     * queries instead of one lookup per name.
     */
    Map<UUID, String> enrollmentStatusByStudent(UUID courseId, Collection<UUID> studentIds);

    void saveEnrollment(UUID studentId, UUID courseId);

    /** Reopens the closed enrolments these students hold in the course, in one statement. */
    void reactivateEnrollments(UUID courseId, Collection<UUID> studentIds);

    PageResult<CourseStudent> studentsByCourse(UUID courseId, PageQuery pageQuery);

    /**
     * Only the enrollments still in force for the course. Operational sheets that are filled in day
     * after day — attendance above all — must not keep listing a student who was withdrawn.
     * Year-end academic records keep using {@link #studentsByCourse}, because a withdrawn student
     * still owns the grades they earned before leaving.
     */
    PageResult<CourseStudent> activeStudentsByCourse(UUID courseId, PageQuery pageQuery);

    UUID courseOfEnrollment(UUID courseEnrollmentId);

    /**
     * Course of each given enrollment, in a single query. Ids that do not exist are absent from the
     * result, which lets a whole-roster authorization check both resolve and validate without one
     * lookup per student.
     */
    Map<UUID, UUID> courseIdsByEnrollment(Collection<UUID> courseEnrollmentIds);

    /**
     * Courses the student is enrolled in, whatever the enrollment status. Used to decide who may
     * read the student, so a withdrawn enrollment still ties them to their former teachers.
     */
    List<UUID> courseIdsOfStudent(UUID studentId);

    /**
     * How many students each classroom of a gestión holds, asked once for all of them.
     *
     * <p>A course with no enrolments has no entry: a grouped count answers about the rows that
     * exist, and the caller reads a missing key as zero rather than being handed an invented one.
     */
    Map<UUID, Long> enrolmentCountsByCourse(Integer academicYearId);

    Optional<CourseStudent> courseStudentById(UUID courseEnrollmentId);

    int withdrawActiveEnrollments(UUID studentId);

    /**
     * How many active enrolments of the course hold each stored gender value, grouped once rather
     * than counted by scanning a (paged) roster.
     *
     * <p>Keys are the raw stored values — {@code "M"} and {@code "F"} in practice, since that is
     * all {@code CreateStudentRequest} accepts — and {@code gender} is nullable on a student, so a
     * student with none is absent from the map rather than counted under either key.
     */
    Map<String, Long> genderCountsOfCourse(UUID courseId);

    /**
     * How many students are on the course's roll right now.
     *
     * <p>The companion of {@link #genderCountsOfCourse}: same population, counted whole. It exists
     * because the two gender counts add up to it only when every student has a gender on record,
     * and deriving the roll from them would quietly lose the ones who do not. It is also not the
     * academic roster's total, which keeps a withdrawn student on purpose — see {@link
     * #activeStudentsByCourse}.
     */
    long activeEnrolmentCountOfCourse(UUID courseId);
}
