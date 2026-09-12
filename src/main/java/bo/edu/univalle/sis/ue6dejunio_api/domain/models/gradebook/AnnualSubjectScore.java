package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One area of knowledge across the whole year: its three trimester totals and the average the
 * school's sheet calls {@code PR}.
 *
 * @param classGroupId the class group this area is taught as.
 * @param subjectName  the area as the sheet heads it.
 * @param trimester1   the total for that trimester, or null when the area has no row there at all.
 *                     Null is "never graded here", which is not the same as a zero.
 * @param trimester2   as {@code trimester1}, for the second trimester.
 * @param trimester3   as {@code trimester1}, for the third trimester.
 * @param average      the mean of the trimesters this area was actually graded in. A subject that
 *                     starts mid-year is averaged over the trimesters it existed for, never over
 *                     three — dividing by three would invent failed trimesters for a course the
 *                     student never had.
 */
public record AnnualSubjectScore(
    UUID classGroupId,
    String subjectName,
    BigDecimal trimester1,
    BigDecimal trimester2,
    BigDecimal trimester3,
    BigDecimal average
) {}
