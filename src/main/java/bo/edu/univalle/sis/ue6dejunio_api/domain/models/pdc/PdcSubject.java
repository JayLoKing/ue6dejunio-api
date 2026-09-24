package bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc;

import java.util.List;
import java.util.UUID;

/**
 * One subject's block of a plan. The knowledge area and the names come along because the printed
 * block heads itself with them, and the reader of a plan should not have to resolve them again.
 *
 * <p>{@code teacherId} is who teaches the block: the ownership guard reads it, so it travels with
 * the plan. {@code generalAdaptations} holds the strategies the class needs, as opposed to the ones
 * a named student needs.
 */
public record PdcSubject(
        UUID id,
        UUID classGroupId,
        String subjectName,
        String knowledgeArea,
        UUID teacherId,
        String teacherName,
        String learningObjective,
        String generalAdaptations,
        Integer displayOrder,
        List<PdcEntry> entries) {

    public PdcSubject {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
