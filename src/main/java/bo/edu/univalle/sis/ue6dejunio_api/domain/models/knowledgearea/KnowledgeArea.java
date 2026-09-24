package bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea;

/**
 * One of the areas of knowledge the curriculum groups subjects under.
 *
 * @param displayOrder where the area sits on the printed plan. Data rather than a convention in the
 *     view, because the plan is laid out in this order and the school decides it
 */
public record KnowledgeArea(Integer id, String name, Integer displayOrder) {}
