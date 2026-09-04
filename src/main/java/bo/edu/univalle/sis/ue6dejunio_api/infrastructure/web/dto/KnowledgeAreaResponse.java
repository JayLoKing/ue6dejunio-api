package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.KnowledgeArea;

public record KnowledgeAreaResponse(Integer id, String name, Integer displayOrder) {
    public static KnowledgeAreaResponse from(KnowledgeArea a) {
        return new KnowledgeAreaResponse(a.id(), a.name(), a.displayOrder());
    }
}
