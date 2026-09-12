package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentAnnualSummary;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * One student's year as the three year-end sheets read it: the per-area matrix, the trimester
 * averages, and the final average the ranking is ordered by. All three come from this one payload,
 * so the arithmetic behind them lives in one place instead of three screens.
 */
public record StudentAnnualSummaryResponse(
    UUID courseEnrollmentId,
    UUID studentId,
    String fullName,
    List<Subject> subjects,
    List<BigDecimal> trimesterAverages,
    BigDecimal finalAverage
) {
    /** A null trimester means the area has no row there; {@code average} covers only the
     * trimesters it was graded in. */
    public record Subject(
        UUID classGroupId,
        String subjectName,
        BigDecimal trimester1,
        BigDecimal trimester2,
        BigDecimal trimester3,
        BigDecimal average
    ) {}

    public static StudentAnnualSummaryResponse from(StudentAnnualSummary s) {
        List<Subject> subs = s.subjects().stream()
            .map(x -> new Subject(x.classGroupId(), x.subjectName(),
                x.trimester1(), x.trimester2(), x.trimester3(), x.average()))
            .toList();
        // A fixed-length list indexed by trimester, not a map: the sheet always has three columns,
        // and a missing key would read as "no such trimester" instead of "nothing graded yet".
        // Arrays.asList and not List.of, which rejects the nulls this list is required to carry.
        List<BigDecimal> averages = Arrays.asList(
            s.trimester1Average(), s.trimester2Average(), s.trimester3Average());
        return new StudentAnnualSummaryResponse(s.courseEnrollmentId(), s.studentId(), s.fullName(),
            subs, averages, s.finalAverage());
    }
}
