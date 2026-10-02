package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Which closed years a set of academic records belongs to. Each method answers in one query however
 * many ids it is given, so a batch write asks once and never once per row.
 *
 * <p>An id that matches nothing belongs to no year, so it contributes nothing to the answer. The
 * caller reads an empty set as "no closed year in the way", which leaves the use case's own
 * not-found handling to report an unknown target.
 */
public interface IAcademicYearLockDomain {

    /** Year numbers (2026, not the row id) of the closed years the given class groups sit in. */
    Set<Integer> closedYearsOfClassGroups(Collection<UUID> classGroupIds);

    /** Year numbers of the closed years the given course enrollments sit in. */
    Set<Integer> closedYearsOfEnrollments(Collection<UUID> courseEnrollmentIds);

    /** Year numbers of the closed years the given courses sit in. */
    Set<Integer> closedYearsOfCourses(Collection<UUID> courseIds);

    /** Year numbers of the closed years the given curricular plans (PDC) sit in. */
    Set<Integer> closedYearsOfPlans(Collection<UUID> planIds);

    /** Year numbers of the closed years the given risk predictions sit in. */
    Set<Integer> closedYearsOfRiskPredictions(Collection<UUID> predictionIds);
}
