package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The informe pedagógico of one course for one trimester, as the school's document reads.
 *
 * <p>Four sections, and only one of them is stored:
 *
 * <ul>
 *   <li><b>I. Datos referenciales</b> — the classroom fields below, plus the school's own heading.
 *   <li><b>II. Logros y dificultades</b> — {@code achievements} and {@code difficulties}, written.
 *   <li><b>III. Estadística</b> — {@code stats}, derived from the roster and the marks.
 *   <li><b>IV. Cuadro de estudiantes reprobados</b> — {@code failingStudents}, the marks derived
 *       and the two rightmost columns written.
 * </ul>
 *
 * <p>The school's heading — district, department, dependency, level — is deliberately absent, for
 * the same reason {@code StudentReportCardResponse} leaves it out: it is identical on every
 * document the school has ever printed and is read once from {@code /api/institution}. Repeating it
 * here would give a reader two copies of the school's own name, free to disagree.
 *
 * @param exists whether a teacher has ever saved this report. False means every written field below
 *     is null because the document has not been started — not because it was started and left
 *     empty. The screen needs to tell those two apart to know whether it is opening a draft or a
 *     blank form.
 * @param updatedAt when it was last saved, or null when it never was.
 */
public record PedagogicalReportSheet(
        UUID courseId,
        String gradeName,
        String parallelName,
        Integer year,
        String homeroomTeacherName,
        Integer trimester,
        boolean exists,
        String achievements,
        String difficulties,
        PedagogicalReportStats stats,
        List<FailingStudentRow> failingStudents,
        LocalDateTime updatedAt) {}
