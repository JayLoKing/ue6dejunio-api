package bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea;

/**
 * @param displayOrder optional. Absent, the area goes last, which is where a new one belongs — the
 *     order is where it prints on the plan, not something the school weighs while naming it
 */
public record CreateKnowledgeAreaCommand(String name, Integer displayOrder) {}
