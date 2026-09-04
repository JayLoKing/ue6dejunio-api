package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

/**
 * @param studentsReadmitted how many of the existing ones were off the roll and were put back.
 *                           Counted apart from {@code studentsExisting} because it is the one
 *                           number in here that reports a change to a student the import did not
 *                           create, and a school re-registering someone should see that it happened
 */
public record EnrollResult(
    int studentsTotal,
    int studentsCreated,
    int studentsExisting,
    int studentsReadmitted,
    int enrollmentsCreated,
    int enrollmentsSkipped
) {}
