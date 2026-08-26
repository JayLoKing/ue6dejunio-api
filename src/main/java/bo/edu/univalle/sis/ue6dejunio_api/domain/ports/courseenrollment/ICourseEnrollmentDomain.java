package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ICourseEnrollmentDomain {
    boolean courseExists(UUID courseId);

    /** Which of these students already sit in the course, asked once for the whole set. */
    List<UUID> enrolledStudentIds(UUID courseId, Collection<UUID> studentIds);
    void saveEnrollment(UUID studentId, UUID courseId);
    PageResult<CourseStudent> studentsByCourse(UUID courseId, PageQuery pageQuery);

    /**
     * Only the enrollments still in force for the course. Operational sheets that are filled in
     * day after day — attendance above all — must not keep listing a student who was withdrawn.
     * Year-end academic records keep using {@link #studentsByCourse}, because a withdrawn student
     * still owns the grades they earned before leaving.
     */
    PageResult<CourseStudent> activeStudentsByCourse(UUID courseId, PageQuery pageQuery);
    UUID courseOfEnrollment(UUID courseEnrollmentId);

    /** Course of each given enrollment, in a single query. Ids that do not exist are absent from
     * the result, which lets a whole-roster authorization check both resolve and validate without
     * one lookup per student. */
    Map<UUID, UUID> courseIdsByEnrollment(Collection<UUID> courseEnrollmentIds);

    /** Courses the student is enrolled in, whatever the enrollment status. Used to decide who may
     * read the student, so a withdrawn enrollment still ties them to their former teachers. */
    List<UUID> courseIdsOfStudent(UUID studentId);
    Optional<CourseStudent> courseStudentById(UUID courseEnrollmentId);
    int withdrawActiveEnrollments(UUID studentId);
}
