package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;

/**
 * @param studentsReadmitted how many of the existing ones were off the roll and were put back by
 *     this import. Reported on its own because it is a change to somebody the import did not
 *     create, and the school should see that it happened
 */
public record EnrollResponse(
        int studentsTotal,
        int studentsCreated,
        int studentsExisting,
        int studentsReadmitted,
        int enrollmentsCreated,
        int enrollmentsSkipped) {
    public static EnrollResponse from(EnrollResult r) {
        return new EnrollResponse(
                r.studentsTotal(),
                r.studentsCreated(),
                r.studentsExisting(),
                r.studentsReadmitted(),
                r.enrollmentsCreated(),
                r.enrollmentsSkipped());
    }
}
