package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.GenderTally;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportSheet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One course's informe pedagógico for one trimester, four sections of it.
 *
 * <p>The school's heading — district, department, dependency, level — is not here. It is the same
 * on every document the school prints and is read once from {@code /api/institution}, exactly as
 * {@link StudentReportCardResponse} leaves it out. Two copies of the school's own name is one more
 * than can be kept in agreement.
 */
public record PedagogicalReportResponse(
    UUID courseId,
    String gradeName,
    String parallelName,
    Integer year,
    String homeroomTeacherName,
    Integer trimester,
    boolean exists,
    String achievements,
    String difficulties,
    Stats stats,
    List<FailingStudent> failingStudents,
    LocalDateTime updatedAt
) {

    /**
     * Section III. The three totals need not add up: a student nobody marked is effective and
     * neither passed nor failed, and {@code male + female} can fall under {@code total} because
     * a student's gender may not be on record.
     */
    public record Stats(Tally effective, Tally passed, Tally failed) {}

    /** One {@code V | M | T | %} column. A null percentage means there is no effective roster. */
    public record Tally(int male, int female, int total, BigDecimal percentage) {}

    /**
     * One row of section IV. The document prints {@code failedAreas} stacked inside a single cell,
     * one area and its mark per line.
     */
    public record FailingStudent(
        int number,
        UUID courseEnrollmentId,
        UUID studentId,
        String fullName,
        List<Area> failedAreas,
        String actions,
        String verificationSource
    ) {}

    public record Area(UUID classGroupId, String subjectName, BigDecimal mark) {}

    public static PedagogicalReportResponse from(PedagogicalReportSheet sheet) {
        List<FailingStudent> failing = sheet.failingStudents().stream()
            .map(row -> new FailingStudent(row.number(), row.courseEnrollmentId(), row.studentId(),
                row.fullName(),
                row.failedAreas().stream()
                    .map(a -> new Area(a.classGroupId(), a.subjectName(), a.mark()))
                    .toList(),
                row.actions(), row.verificationSource()))
            .toList();
        return new PedagogicalReportResponse(sheet.courseId(), sheet.gradeName(),
            sheet.parallelName(), sheet.year(), sheet.homeroomTeacherName(), sheet.trimester(),
            sheet.exists(), sheet.achievements(), sheet.difficulties(),
            new Stats(tally(sheet.stats().effective()), tally(sheet.stats().passed()),
                tally(sheet.stats().failed())),
            failing, sheet.updatedAt());
    }

    private static Tally tally(GenderTally t) {
        return new Tally(t.male(), t.female(), t.total(), t.percentage());
    }
}
