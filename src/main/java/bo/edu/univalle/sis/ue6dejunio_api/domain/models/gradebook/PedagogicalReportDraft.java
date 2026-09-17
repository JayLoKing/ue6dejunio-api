package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.util.List;

/**
 * One save of the informe pedagógico: the written document as the teacher has it right now.
 *
 * <p>{@code achievements} and {@code difficulties} are replaced outright. A null there is a box the
 * teacher emptied, because the screen holds both while it is being filled and always sends both.
 *
 * <p><b>{@code notes} is the one field where null and empty are not the same thing.</b>
 * <ul>
 *   <li>{@code null} — this save says nothing about section IV. Every stored paragraph is left
 *       exactly where it was.
 *   <li>an empty list — this save says section IV holds nothing. Every stored paragraph is deleted.
 *   <li>a list — section IV holds these and only these. Whoever is missing loses their paragraph.
 * </ul>
 *
 * <p>The distinction is not decoration. These paragraphs are prose a teacher typed and nothing in
 * this system keeps a second copy: no versioning, no soft delete, no confirmation between the
 * request and the loss. A caller that saves the prose alone — the obvious partial update, and the
 * shape of the simplest possible request body — must not take the whole of section IV with it.
 *
 * <p>Carries no course and no trimester. Those name <em>which</em> report is being written and
 * arrive beside the draft, so a body can never contradict the document the caller addressed.
 */
public record PedagogicalReportDraft(
    String achievements,
    String difficulties,
    List<PedagogicalReportNote> notes
) {}
