package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ICourseEnrollmentDomain {
    boolean courseExists(UUID courseId);
    boolean existsEnrollment(UUID studentId, UUID courseId);
    void saveEnrollment(UUID studentId, UUID courseId);
    Optional<UUID> findByStudentAndCourse(UUID studentId, UUID courseId);
    Page<CourseStudent> studentsByCourse(UUID courseId, Pageable pageable);

    /**
     * Only the enrollments still in force for the course. Operational sheets that are filled in
     * day after day — attendance above all — must not keep listing a student who was withdrawn.
     * Year-end academic records keep using {@link #studentsByCourse}, because a withdrawn student
     * still owns the grades they earned before leaving.
     */
    Page<CourseStudent> activeStudentsByCourse(UUID courseId, Pageable pageable);
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
