package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollConflict;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrolledElsewhere;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.ReinstatedStudent;
import java.util.List;
import java.util.UUID;

/**
 * @param studentsFromPreviousYear existing students who held an Effective enrolment in the gestión
 *     before the course's own. With {@code studentsCreated}, {@code studentsReentering}, {@code
 *     reinstated}, {@code skippedWithdrawn} and {@code skippedOtherCourse} they add up to the
 *     distinct students the file named
 * @param studentsReentering existing students, not withdrawn, with no Effective enrolment in that
 *     gestión: they come back after a gap
 * @param reinstated students withdrawn in an earlier gestión that the load returned to Effective
 *     and seated: this year's roster naming them is the school taking them back
 * @param skippedWithdrawn the students withdrawn in the course's own gestión that the load left
 *     out. Reported so the school knows who did not come in: only the Director's reinstatement puts
 *     them back
 * @param skippedOtherCourse the students the load left out because they already sit in another
 *     course of the same gestión, with that course
 * @param conflicts the rows left out because their RUDE code and identity card belong to two
 *     different students. Outside the sum above: such a row names no single student
 */
public record EnrollResponse(
        int studentsTotal,
        int studentsCreated,
        int studentsExisting,
        int studentsFromPreviousYear,
        int studentsReentering,
        int enrollmentsCreated,
        int enrollmentsSkipped,
        List<ReinstatedResponse> reinstated,
        List<SkippedStudentResponse> skippedWithdrawn,
        List<EnrolledElsewhereResponse> skippedOtherCourse,
        List<ConflictResponse> conflicts) {
    public static EnrollResponse from(EnrollResult r) {
        return new EnrollResponse(
                r.studentsTotal(),
                r.studentsCreated(),
                r.studentsExisting(),
                r.studentsFromPreviousYear(),
                r.studentsReentering(),
                r.enrollmentsCreated(),
                r.enrollmentsSkipped(),
                r.reinstated().stream().map(ReinstatedResponse::from).toList(),
                r.skippedWithdrawn().stream().map(SkippedStudentResponse::from).toList(),
                r.skippedOtherCourse().stream().map(EnrolledElsewhereResponse::from).toList(),
                r.conflicts().stream().map(ConflictResponse::from).toList());
    }

    /** A row left out because its RUDE code and identity card belong to different students. */
    public record ConflictResponse(
            String rudeCode, String identityCard, String fullName, String reason) {
        static ConflictResponse from(EnrollConflict c) {
            return new ConflictResponse(c.rudeCode(), c.identityCard(), c.fullName(), c.reason());
        }
    }

    /** A student withdrawn in an earlier gestión that the load took back and seated. */
    public record ReinstatedResponse(UUID studentId, String fullName) {
        static ReinstatedResponse from(ReinstatedStudent s) {
            return new ReinstatedResponse(s.studentId(), s.fullName());
        }
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
