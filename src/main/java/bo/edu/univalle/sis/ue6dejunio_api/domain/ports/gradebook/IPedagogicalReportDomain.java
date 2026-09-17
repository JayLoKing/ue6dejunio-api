package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReport;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.PedagogicalReportDraft;

import java.util.Optional;
import java.util.UUID;

/** Where the written half of the informe pedagógico is kept. */
public interface IPedagogicalReportDomain {

    /**
     * The report a teacher saved for this course and trimester, notes included, or empty when they
     * never started one.
     *
     * <p>Empty and not a blank report: the sheet has to be able to say "never written" apart from
     * "written and cleared", and only the caller that reads this can tell the difference.
     */
    Optional<PedagogicalReport> byCourseAndTrimester(UUID courseId, int trimester);

    /**
     * Writes the whole document, creating it the first time and correcting it every time after.
     *
     * <p>{@code (id_course, trimester)} is unique, so there is one report to correct rather than a
     * second opinion to file beside the first — the school signs one informe per trimester, and two
     * rows would leave nobody able to say which.
     *
     * <p>When the draft carries a note list, it is applied as a set and not merged: a student
     * absent from it has their paragraph deleted, because that list is the whole of section IV as
     * the teacher has it. Rows that survive keep their {@code created_at} — the teacher is
     * correcting a paragraph they already wrote, not writing a new one.
     *
     * <p>When the draft's notes are {@code null}, section IV is left untouched. That is a save
     * about the prose alone, and reading it as an empty section IV would delete every paragraph on
     * the report — prose the teacher typed, of which nothing here keeps a second copy.
     */
    PedagogicalReport save(UUID courseId, int trimester, PedagogicalReportDraft draft);
}
