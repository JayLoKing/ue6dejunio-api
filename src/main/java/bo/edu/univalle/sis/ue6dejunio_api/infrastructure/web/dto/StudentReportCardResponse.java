package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.StudentReportCard;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * One student's libreta.
 *
 * <p>The school's heading is not here. It is the same on every libreta ever printed and is read
 * once from {@code /api/institution}; repeating it per student would send the same eight strings
 * back thirty times to print one course.
 */
public record StudentReportCardResponse(
    UUID courseEnrollmentId,
    UUID studentId,
    String rudeCode,
    String fullName,
    String gradeName,
    String parallelName,
    Integer year,
    List<Field> fields,
    List<BigDecimal> trimesterAverages,
    BigDecimal finalAverage,
    String finalAverageInWords,
    List<Outcome> trimesterOutcomes
) {
    /** A field of knowledge with the curricular areas under it. */
    public record Field(String fieldName, Integer displayOrder, List<Area> areas) {}

    /** A null trimester means the area has no row there; never graded, which is not a zero. */
    public record Area(
        UUID classGroupId,
        String subjectName,
        BigDecimal trimester1,
        BigDecimal trimester2,
        BigDecimal trimester3,
        BigDecimal average
    ) {}

    /** Areas passed and failed that trimester. They need not add up: an area with no mark is
     * counted in neither. */
    public record Outcome(int trimester, int passedAreas, int failedAreas) {}

    public static StudentReportCardResponse from(StudentReportCard c) {
        List<Field> fields = c.fields().stream()
            .map(f -> new Field(f.fieldName(), f.displayOrder(), f.subjects().stream()
                .map(s -> new Area(s.classGroupId(), s.subjectName(),
                    s.trimester1(), s.trimester2(), s.trimester3(), s.average()))
                .toList()))
            .toList();
        List<Outcome> outcomes = c.trimesterOutcomes().stream()
            .map(o -> new Outcome(o.trimester(), o.passedAreas(), o.failedAreas()))
            .toList();
        // Arrays.asList and not List.of, which rejects the nulls this list is required to carry.
        List<BigDecimal> averages = Arrays.asList(
            c.trimester1Average(), c.trimester2Average(), c.trimester3Average());
        return new StudentReportCardResponse(c.courseEnrollmentId(), c.studentId(), c.rudeCode(),
            c.fullName(), c.gradeName(), c.parallelName(), c.year(), fields, averages,
            c.finalAverage(), c.finalAverageInWords(), outcomes);
    }
}
