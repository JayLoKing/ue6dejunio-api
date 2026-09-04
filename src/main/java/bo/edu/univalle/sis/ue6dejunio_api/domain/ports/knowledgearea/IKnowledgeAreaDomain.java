package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.knowledgearea;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.KnowledgeArea;

import java.util.Optional;

public interface IKnowledgeAreaDomain {
    KnowledgeArea save(KnowledgeArea area);
    Optional<KnowledgeArea> findById(Integer id);
    boolean existsByName(String name);

    /** Whether any subject names this area. What stands between the area and being deleted. */
    boolean hasSubjects(Integer areaId);

    /** The last position on the plan, or 0 when there are no areas yet. */
    int maxDisplayOrder();

    PageResult<KnowledgeArea> list(PageQuery pageQuery);
    void deleteById(Integer id);
}
