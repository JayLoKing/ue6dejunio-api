package bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea;

/**
 * @param displayOrder optional. Absent, the area stays where it was: an edit that says nothing
 *                     about the order is not asking for it to be reset
 */
public record UpdateKnowledgeAreaCommand(String name, Integer displayOrder) {}
