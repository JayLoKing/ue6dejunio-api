package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import java.util.List;

/**
 * What a roster load did.
 *
 * <p>Two views of the same load. The row counts ({@code studentsTotal}, {@code studentsExisting},
 * {@code enrollmentsCreated}, {@code enrollmentsSkipped}) are per row of the file, repeats
 * included. The student groups ({@code studentsCreated}, {@code studentsFromPreviousYear}, {@code
 * studentsReentering}, {@code skippedWithdrawn}, {@code skippedOtherCourse}) are per distinct
 * student and partition the distinct students the file named:
 *
 * <pre>
 * studentsCreated + studentsFromPreviousYear + studentsReentering + skippedWithdrawn.size()
 *     + skippedOtherCourse.size() == distinct students in the request
 * </pre>
 *
 * @param studentsTotal how many rows the request carried, repeated rows and skipped ones included
 * @param studentsCreated how many of them were not on record and were created
 * @param studentsExisting how many rows matched a student already on record, withdrawn ones
 *     included, and a repeated row of a student created by this same load
 * @param studentsFromPreviousYear how many distinct students already on record held an Effective
 *     enrolment in the gestión before the course's own. Withdrawn students are not counted
 * @param studentsReentering how many distinct students already on record, not withdrawn, held no
 *     Effective enrolment in that gestión: they come back after a gap, or no earlier gestión exists
 * @param enrollmentsCreated how many students were seated in the course, whether by a new row or by
 *     reopening a closed one
 * @param enrollmentsSkipped how many rows matched a student who already holds a seat in the course,
 *     or repeated one seated earlier in the same request. Not the withdrawn ones: those are in
 *     {@code skippedWithdrawn}
 * @param skippedWithdrawn the withdrawn students the load left out, once each. Only the Director's
 *     explicit reinstatement puts one back, so a load neither seats them nor changes their status
 * @param skippedOtherCourse the students the load left out because they already hold an Effective
 *     seat in another course of the same gestión, once each, with the course they sit in
 */
public record EnrollResult(
        int studentsTotal,
        int studentsCreated,
        int studentsExisting,
        int studentsFromPreviousYear,
        int studentsReentering,
        int enrollmentsCreated,
        int enrollmentsSkipped,
        List<SkippedStudent> skippedWithdrawn,
        List<EnrolledElsewhere> skippedOtherCourse) {}
