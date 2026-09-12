package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One student's whole year. Backs three of the school's sheets from a single read: the per-area
 * matrix, the three trimester averages, and the final average their year-end ranking is sorted by.
 *
 * <p>The school's template carries two columns both titled a final average — one under the area
 * matrix, one under the trimester averages. They agree only while every area is graded in all
 * three trimesters, which is the state a closed year is in. {@code finalAverage} is the area one,
 * because that is the column the year-end sheet titles PROMEDIO FINAL and the one the ranking
 * reads. Reporting both would be handing the school two different answers to one question.
 *
 * @param courseEnrollmentId the enrollment this year belongs to.
 * @param studentId          the student behind that enrollment.
 * @param fullName           the student as the sheet names them.
 * @param subjects           one entry per area of knowledge, ordered so the sheet's columns hold
 *                           still between two loads.
 * @param trimester1Average  null when the student has no graded row that trimester. Exactly what
 *                           the trimester centralizer reports as their general average, so the
 *                           annual and the trimester screens can never disagree about a student.
 * @param trimester2Average  as {@code trimester1Average}, for the second trimester.
 * @param trimester3Average  as {@code trimester1Average}, for the third trimester.
 * @param finalAverage       the mean of the area averages, or null when nothing is graded yet.
 */
public record StudentAnnualSummary(
    UUID courseEnrollmentId,
    UUID studentId,
    String fullName,
    List<AnnualSubjectScore> subjects,
    BigDecimal trimester1Average,
    BigDecimal trimester2Average,
    BigDecimal trimester3Average,
    BigDecimal finalAverage
) {}
