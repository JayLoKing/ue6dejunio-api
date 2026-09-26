package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.CourseRiskSummary;
import java.util.UUID;

/**
 * One classroom's risk bands.
 *
 * <p>The counts are students, not predictions — a child failing three areas is one child — and
 * {@code unpredicted} is how much of the roll the sweep has not reached, which is what lets a
 * reader tell an unswept classroom from a safe one.
 *
 * <p>DO NOT DERIVE THE ENROLMENT BY ADDING THE FIVE. They usually add up to it and are not
 * guaranteed to: a withdrawn student keeps their predictions while leaving the roll, so a swept
 * classroom that since lost somebody holds more predicted students than enrolled ones, and {@code
 * unpredicted} is floored at zero rather than going negative. The academic summary's {@code
 * students} is the enrolment; this is the shape of who has been looked at.
 */
public record CourseRiskSummaryResponse(
        UUID courseId,
        String gradeName,
        String parallelName,
        int critical,
        int atRisk,
        int safe,
        int outstanding,
        int unpredicted) {

    public static CourseRiskSummaryResponse from(CourseRiskSummary s) {
        return new CourseRiskSummaryResponse(
                s.courseId(),
                s.gradeName(),
                s.parallelName(),
                s.critical(),
                s.atRisk(),
                s.safe(),
                s.outstanding(),
                s.unpredicted());
    }
}
