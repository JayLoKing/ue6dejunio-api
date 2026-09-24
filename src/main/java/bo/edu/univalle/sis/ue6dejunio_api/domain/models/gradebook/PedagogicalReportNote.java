package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.util.UUID;

/**
 * What the teacher wrote about one failing student in section IV of the informe pedagógico.
 *
 * <p>Only the two columns a person types. The areas the student failed and the marks beside them
 * are read from {@code academic_scores} when the sheet is drawn, so they are not here — a stored
 * copy would stop being true the moment a mark is corrected.
 *
 * <p>Keyed by the enrolment and not by a row id of its own. The pair (report, enrolment) is what
 * the table is unique on and what a save addresses, so the surrogate key is the adapter's business
 * and nothing above it ever needs to name a row.
 *
 * <p>Both fields may be null: a report half written has to be savable, and a teacher who filled the
 * actions and not yet the source is in the middle of their work, not in error.
 */
public record PedagogicalReportNote(
        UUID courseEnrollmentId, String actions, String verificationSource) {}
