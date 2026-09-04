package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.knowledgearea;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.CreateKnowledgeAreaCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.KnowledgeArea;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.UpdateKnowledgeAreaCommand;

public interface IKnowledgeAreaService {
    KnowledgeArea create(CreateKnowledgeAreaCommand command);
    KnowledgeArea update(Integer id, UpdateKnowledgeAreaCommand command);
    KnowledgeArea getById(Integer id);
    PageResult<KnowledgeArea> list(PageQuery pageQuery);
    void delete(Integer id);
}
