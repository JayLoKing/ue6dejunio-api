package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The part of the informe pedagógico that is stored: everything on the sheet a person writes.
 *
 * <p>Section II ({@code achievements}, {@code difficulties}) and the notes of section IV, and
 * nothing else. Sections I and III and the marks of section IV are derived at render time from the
 * institution, the course and {@code academic_scores} — see {@code PedagogicalReportSheet}, which
 * is what a reader actually gets.
 *
 * @param updatedAt when the teacher last saved, which is the only thing that dates this document.
 */
public record PedagogicalReport(
    UUID id,
    UUID courseId,
    Integer trimester,
    String achievements,
    String difficulties,
    List<PedagogicalReportNote> notes,
    LocalDateTime updatedAt
) {}
