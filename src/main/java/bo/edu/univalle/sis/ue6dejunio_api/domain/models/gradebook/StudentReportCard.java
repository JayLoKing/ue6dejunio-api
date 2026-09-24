package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One student's libreta: their year grouped the way their report card prints it.
 *
 * <p>The school's heading — district, department, dependency, shift — is deliberately absent. It is
 * the same on every libreta ever printed and is read once from {@code /api/institution}; copying it
 * into every student's payload would be sending the same eight strings back for each of thirty
 * children.
 *
 * @param rudeCode what the libreta identifies a student by, above their own name.
 * @param gradeName the year of schooling, as the sheet words it.
 * @param year the gestión, taken from the course rather than asked for.
 * @param fields the fields of knowledge in sheet order, each with its areas. A field the student
 *     has no marked area in is left out.
 * @param trimesterOutcomes always three, one per trimester, even before the year is over.
 * @param finalAverage the mean of the area averages, or null when nothing is graded.
 * @param finalAverageInWords the same number spelled out, blank when there is none. The libreta
 *     carries both and they must never name two different marks.
 */
public record StudentReportCard(
        UUID courseEnrollmentId,
        UUID studentId,
        String rudeCode,
        String fullName,
        String gradeName,
        String parallelName,
        Integer year,
        List<KnowledgeFieldRow> fields,
        BigDecimal trimester1Average,
        BigDecimal trimester2Average,
        BigDecimal trimester3Average,
        BigDecimal finalAverage,
        String finalAverageInWords,
        List<TrimesterOutcome> trimesterOutcomes) {}
