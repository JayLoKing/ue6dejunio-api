package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.Level;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.level.ILevelDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.LevelEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaGradeRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaLevelRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class LevelRepositoryAdapter implements ILevelDomain {

    private final JpaLevelRepository levelRepo;
    private final JpaGradeRepository gradeRepo;

    public LevelRepositoryAdapter(JpaLevelRepository levelRepo, JpaGradeRepository gradeRepo) {
        this.levelRepo = levelRepo;
        this.gradeRepo = gradeRepo;
    }

    @Override
    @Transactional
    public Level save(Level level) {
        LevelEntity e = level.id() == null
            ? new LevelEntity()
            : levelRepo.findById(level.id()).orElseGet(LevelEntity::new);
        e.setName(level.name());
        if (level.id() != null) e.setId(level.id());
        return toDomain(levelRepo.save(e));
    }

    @Override
    public Optional<Level> findById(Integer id) {
        return levelRepo.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return levelRepo.existsByName(name);
    }

    @Override
    public boolean hasGrades(Integer levelId) {
        return gradeRepo.existsByLevel_Id(levelId);
    }

    @Override
    public PageResult<Level> list(PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        return SpringPaging.toPageResult(levelRepo.findAll(pageable).map(this::toDomain));
    }

    @Override
    @Transactional
    public void deleteById(Integer id) {
        levelRepo.deleteById(id);
    }

    private Level toDomain(LevelEntity e) {
        return new Level(e.getId(), e.getName());
    }
}
