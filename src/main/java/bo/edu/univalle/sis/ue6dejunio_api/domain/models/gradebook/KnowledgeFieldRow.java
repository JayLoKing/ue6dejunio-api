package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

import java.util.List;

/**
 * One field of knowledge on the libreta, with the curricular areas that hang under it.
 *
 * <p>A field the student has no marked area in is left out rather than printed empty: a heading
 * over nothing reads as a subject whose marks went missing.
 *
 * @param displayOrder the order the school's sheets read the fields in.
 */
public record KnowledgeFieldRow(
    String fieldName,
    Integer displayOrder,
    List<AnnualSubjectScore> subjects
) {}
