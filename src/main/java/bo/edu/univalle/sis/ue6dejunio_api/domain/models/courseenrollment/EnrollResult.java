package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

import java.util.List;

/**
 * What a roster load did.
 *
 * @param studentsTotal how many rows the request carried, repeated rows and skipped ones included
 * @param studentsCreated how many of them were not on record and were created
 * @param studentsExisting how many rows matched a student already on record, withdrawn ones
 *     included
 * @param enrollmentsCreated how many students were seated in the course, whether by a new row or by
 *     reopening a closed one
 * @param enrollmentsSkipped how many rows matched a student who already holds a seat in the course,
 *     or repeated one seated earlier in the same request. Not the withdrawn ones: those are in
 *     {@code skippedWithdrawn}
 * @param skippedWithdrawn the withdrawn students the load left out, once each. Only the Director's
 *     explicit reinstatement puts one back, so a load neither seats them nor changes their status
 */
public record EnrollResult(
        int studentsTotal,
        int studentsCreated,
        int studentsExisting,
        int enrollmentsCreated,
        int enrollmentsSkipped,
        List<SkippedStudent> skippedWithdrawn) {}
