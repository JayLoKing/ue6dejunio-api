package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import java.util.List;

/**
 * @param studentsFromPreviousYear existing students who held an Effective enrolment in the gestión
 *     before the course's own. With {@code studentsCreated}, {@code studentsReentering} and {@code
 *     skippedWithdrawn} they add up to the distinct students the file named
 * @param studentsReentering existing students, not withdrawn, with no Effective enrolment in that
 *     gestión: they come back after a gap
 * @param skippedWithdrawn the withdrawn students the load left out. Reported so the school knows
 *     who did not come in: only the Director's reinstatement puts a withdrawn student back
 */
public record EnrollResponse(
        int studentsTotal,
        int studentsCreated,
        int studentsExisting,
        int studentsFromPreviousYear,
        int studentsReentering,
        int enrollmentsCreated,
        int enrollmentsSkipped,
        List<SkippedStudentResponse> skippedWithdrawn) {
    public static EnrollResponse from(EnrollResult r) {
        return new EnrollResponse(
                r.studentsTotal(),
                r.studentsCreated(),
                r.studentsExisting(),
                r.studentsFromPreviousYear(),
                r.studentsReentering(),
                r.enrollmentsCreated(),
                r.enrollmentsSkipped(),
                r.skippedWithdrawn().stream().map(SkippedStudentResponse::from).toList());
    }
}
