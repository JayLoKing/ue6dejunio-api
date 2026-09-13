package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.HonorRollEntry;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One place on the cuadro de honor.
 *
 * <p>The classroom travels with every entry, including on a single course's podium. The reader of
 * a school-wide list needs it to tell two students apart, and one payload that changes shape with
 * the scope asked for is a payload every screen has to branch on.
 */
public record HonorRollEntryResponse(
    int position,
    UUID courseEnrollmentId,
    UUID studentId,
    String fullName,
    UUID courseId,
    String gradeName,
    String parallelName,
    BigDecimal finalAverage
) {
    public static HonorRollEntryResponse from(HonorRollEntry e) {
        return new HonorRollEntryResponse(e.position(), e.courseEnrollmentId(), e.studentId(),
            e.fullName(), e.courseId(), e.gradeName(), e.parallelName(), e.finalAverage());
    }
}
