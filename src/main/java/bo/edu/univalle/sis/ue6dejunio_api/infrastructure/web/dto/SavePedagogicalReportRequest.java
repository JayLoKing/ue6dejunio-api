package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportDraft;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportNote;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The written half of an informe pedagógico, as the screen holds it.
 *
 * <p>The prose is replaced outright: the form carries both boxes at once and always sends both, so
 * a null {@code achievements} is a box the teacher emptied.
 *
 * <p><b>{@code failingStudents} is different, and the difference matters.</b> Omitting the field
 * leaves section IV exactly as it was; sending {@code []} empties it; sending a list replaces it,
 * and a student left out of that list loses their paragraph. Nothing else in this request has a
 * meaning for "absent" that differs from "empty", and this one does because the paragraphs are
 * prose a teacher typed that the system keeps no second copy of — no versioning, no soft delete,
 * nothing between a mistaken request and the loss. Saving the prose alone is the obvious partial
 * update and must not take the whole of section IV with it.
 *
 * <p>Nothing here is required. A report begun and left half written has to be savable, which is why
 * the columns behind it are nullable — a teacher who fills the failing students first and comes
 * back to the prose is in the middle of their work, not in error.
 */
public record SavePedagogicalReportRequest(
        // Bounded at the edge and not in the schema. The columns behind these are `text` on purpose
        // —
        // V17 says a length nobody measured would silently truncate a teacher's work — so nothing
        // below
        // this point would refuse a body of any size. 4000 is what the PDC's prose already uses,
        // and it
        // is several times the longest section II the school's own documents carry.
        @Size(max = 4000, message = "achievements no puede exceder 4000 caracteres")
                String achievements,
        @Size(max = 4000, message = "difficulties no puede exceder 4000 caracteres")
                String difficulties,
        // @NotNull on the element and not only @Valid on the list: @Valid cascades into the
        // elements
        // that exist, so a literal null inside the array reaches no constraint at all and would
        // arrive
        // at toDraft() as a null to dereference — a parseable body turned into a 500 where the
        // sibling
        // case, an object missing its enrolment, correctly answers 400.
        //
        // 200 rows, because section IV holds one line per failing student of one course and no
        // classroom in this school comes near that. It is the same ceiling the service pages the
        // roster
        // at, so a request that fits a real course can never be refused by it.
        @Valid @Size(max = 200, message = "failingStudents no puede exceder 200 estudiantes")
                List<
                                @NotNull(message = "failingStudents no admite elementos nulos")
                                FailingStudentNote>
                        failingStudents) {

    /**
     * What the teacher wrote about one student. The enrolment is required — it names whose
     * paragraph this is, and a note attached to nobody could never be shown again.
     *
     * <p>Both texts are one cell of the school's table, not a section of prose, so they are bounded
     * tighter than section II — at the same 2000 the PDC's per-subject contents use.
     */
    public record FailingStudentNote(
            @NotNull(message = "idCourseEnrollment es obligatorio") UUID idCourseEnrollment,
            @Size(max = 2000, message = "actions no puede exceder 2000 caracteres") String actions,
            @Size(max = 2000, message = "verificationSource no puede exceder 2000 caracteres")
                    String verificationSource) {}

    /**
     * The draft the domain writes.
     *
     * <p>An absent {@code failingStudents} stays null all the way down, where it means "this save
     * says nothing about section IV". Mapping it to an empty list here would say the opposite —
     * that section IV is now empty — and delete every paragraph on the report.
     */
    public PedagogicalReportDraft toDraft() {
        List<PedagogicalReportNote> notes =
                failingStudents == null
                        ? null
                        : failingStudents.stream()
                                .map(
                                        n ->
                                                new PedagogicalReportNote(
                                                        n.idCourseEnrollment(),
                                                        n.actions(),
                                                        n.verificationSource()))
                                .toList();
        return new PedagogicalReportDraft(achievements, difficulties, notes);
    }
}
