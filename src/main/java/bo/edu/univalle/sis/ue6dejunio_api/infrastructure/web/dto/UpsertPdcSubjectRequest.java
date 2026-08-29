package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpsertPdcSubjectCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Writes one subject's block whole: the rows sent replace the rows held, so a week the teacher
 * removed on screen is a week removed here.
 */
public record UpsertPdcSubjectRequest(
    @Size(max = 4000) String learningObjective,
    @Size(max = 4000) String generalAdaptations,
    // A month holds four or five weeks; the cap is loose enough for splits and tight enough that
    // an authenticated write cannot post an unbounded list of free-text rows.
    // Absent and empty are different answers: absent leaves the weeks alone so a step that only
    // touches the objective does not wipe them, empty clears them on purpose.
    @Valid @Size(max = 20) List<Entry> entries
) {

    public record Entry(
        @NotBlank @Size(max = 60) String weekLabel,
        @Size(max = 2000) String contents,
        @Size(max = 2000) String practice,
        @Size(max = 2000) String theory,
        @Size(max = 2000) String valuation,
        @Size(max = 2000) String production,
        @Size(max = 2000) String resources,
        @Min(0) @Max(40) Integer periods,
        @Size(max = 2000) String criteriaBeing,
        @Size(max = 2000) String criteriaKnowing,
        @Size(max = 2000) String criteriaDoing
    ) {}

    public UpsertPdcSubjectCommand toCommand() {
        return new UpsertPdcSubjectCommand(
            learningObjective,
            generalAdaptations,
            entries == null ? null : entries.stream()
                .map(e -> new UpsertPdcSubjectCommand.PdcEntryCommand(
                    e.weekLabel(), e.contents(), e.practice(), e.theory(), e.valuation(),
                    e.production(), e.resources(), e.periods(), e.criteriaBeing(),
                    e.criteriaKnowing(), e.criteriaDoing()))
                .toList());
    }
}
