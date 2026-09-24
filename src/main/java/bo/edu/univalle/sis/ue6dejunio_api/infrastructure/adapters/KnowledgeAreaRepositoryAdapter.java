package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.KnowledgeArea;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.knowledgearea.IKnowledgeAreaDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.KnowledgeAreaEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaKnowledgeAreaRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaSubjectRepository;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class KnowledgeAreaRepositoryAdapter implements IKnowledgeAreaDomain {

    private final JpaKnowledgeAreaRepository areaRepo;
    private final JpaSubjectRepository subjectRepo;

    public KnowledgeAreaRepositoryAdapter(
            JpaKnowledgeAreaRepository areaRepo, JpaSubjectRepository subjectRepo) {
        this.areaRepo = areaRepo;
        this.subjectRepo = subjectRepo;
    }

    @Override
    @Transactional
    public KnowledgeArea save(KnowledgeArea area) {
        KnowledgeAreaEntity e =
                area.id() == null
                        ? new KnowledgeAreaEntity()
                        : areaRepo.findById(area.id()).orElseGet(KnowledgeAreaEntity::new);
        e.setName(area.name());
        e.setDisplayOrder(area.displayOrder());
        if (area.id() != null) {
            e.setId(area.id());
        }
        return toDomain(areaRepo.save(e));
    }

    @Override
    public Optional<KnowledgeArea> findById(Integer id) {
        return areaRepo.findById(id).map(KnowledgeAreaRepositoryAdapter::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return areaRepo.existsByName(name);
    }

    @Override
    public boolean hasSubjects(Integer areaId) {
        return subjectRepo.existsByArea_Id(areaId);
    }

    @Override
    public int maxDisplayOrder() {
        return areaRepo.maxDisplayOrder();
    }

    @Override
    public PageResult<KnowledgeArea> list(PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        return SpringPaging.toPageResult(
                areaRepo.findAll(pageable).map(KnowledgeAreaRepositoryAdapter::toDomain));
    }

    @Override
    @Transactional
    public void deleteById(Integer id) {
        areaRepo.deleteById(id);
    }

    private static KnowledgeArea toDomain(KnowledgeAreaEntity e) {
        return new KnowledgeArea(e.getId(), e.getName(), e.getDisplayOrder());
    }
}
