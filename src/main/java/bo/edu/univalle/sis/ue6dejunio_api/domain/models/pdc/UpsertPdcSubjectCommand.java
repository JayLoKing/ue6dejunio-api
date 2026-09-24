package bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc;

import java.util.List;

/**
 * Writes one subject's block whole: its objective, its adaptations, and every weekly row it holds.
 *
 * <p>The entries replace what the block had rather than merging into it. A teacher who deletes a
 * week expects it gone, and a merge would leave it behind with no way to say so.
 */
public record UpsertPdcSubjectCommand(
        String learningObjective, String generalAdaptations, List<PdcEntryCommand> entries) {

    /** One weekly row as the caller sends it. Ordering comes from the list, not from a field. */
    public record PdcEntryCommand(
            String weekLabel,
            String contents,
            String practice,
            String theory,
            String valuation,
            String production,
            String resources,
            Integer periods,
            String criteriaBeing,
            String criteriaKnowing,
            String criteriaDoing) {}
}
