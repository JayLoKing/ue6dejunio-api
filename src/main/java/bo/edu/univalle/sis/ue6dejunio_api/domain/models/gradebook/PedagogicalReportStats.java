package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

/**
 * Section III of the informe pedagógico: the effective roster, split into who passed the trimester
 * and who did not.
 *
 * <p><b>{@code passed.total() + failed.total()} need not equal {@code effective.total()}, and the
 * gap is the point.</b> A student is failed when at least one of their areas came in under the
 * passing mark, and passed when they were marked in at least one area and failed none. A student
 * with no mark at all that trimester is counted in neither: nobody judged them. That is the same
 * rule {@link TrimesterOutcome} applies area by area — calling an unmarked area failed would tell a
 * parent their child failed a subject nobody graded, and calling it passed is exactly as untrue.
 *
 * <p>By the time the teacher signs this document every effective student has marks, so the three
 * columns do add up in practice. When they do not, the missing students are marks nobody entered,
 * and a sheet that quietly folded them into either column would hide the very gap that has to be
 * closed before the report is handed in.
 */
public record PedagogicalReportStats(
    GenderTally effective,
    GenderTally passed,
    GenderTally failed
) {}
