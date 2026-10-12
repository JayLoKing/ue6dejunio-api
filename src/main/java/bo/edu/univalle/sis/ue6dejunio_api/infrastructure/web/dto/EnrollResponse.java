package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrolledElsewhere;
import java.util.List;
import java.util.UUID;

/**
 * @param studentsFromPreviousYear existing students who held an Effective enrolment in the gestión
 *     before the course's own. With {@code studentsCreated}, {@code studentsReentering}, {@code
 *     skippedWithdrawn} and {@code skippedOtherCourse} they add up to the distinct students the
 *     file named
 * @param studentsReentering existing students, not withdrawn, with no Effective enrolment in that
 *     gestión: they come back after a gap
 * @param skippedWithdrawn the withdrawn students the load left out. Reported so the school knows
 *     who did not come in: only the Director's reinstatement puts a withdrawn student back
 * @param skippedOtherCourse the students the load left out because they already sit in another
 *     course of the same gestión, with that course
 */
public record EnrollResponse(
        int studentsTotal,
        int studentsCreated,
        int studentsExisting,
        int studentsFromPreviousYear,
        int studentsReentering,
        int enrollmentsCreated,
        int enrollmentsSkipped,
        List<SkippedStudentResponse> skippedWithdrawn,
        List<EnrolledElsewhereResponse> skippedOtherCourse) {
    public static EnrollResponse from(EnrollResult r) {
        return new EnrollResponse(
                r.studentsTotal(),
                r.studentsCreated(),
                r.studentsExisting(),
                r.studentsFromPreviousYear(),
                r.studentsReentering(),
                r.enrollmentsCreated(),
                r.enrollmentsSkipped(),
                r.skippedWithdrawn().stream().map(SkippedStudentResponse::from).toList(),
                r.skippedOtherCourse().stream().map(EnrolledElsewhereResponse::from).toList());
    }

    /** A student left out because they already sit in another course of the gestión. */
    public record EnrolledElsewhereResponse(
            UUID studentId, String rudeCode, String fullName, String courseLabel) {
        static EnrolledElsewhereResponse from(EnrolledElsewhere e) {
            return new EnrolledElsewhereResponse(
                    e.studentId(), e.rudeCode(), e.fullName(), e.courseLabel());
        }
    }
}
