package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.parallel.IParallelDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ParallelEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers.ParallelMapper;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaParallelRepository;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ParallelRepositoryAdapter implements IParallelDomain {

    private final JpaParallelRepository parallelRepo;
    private final JpaCourseRepository courseRepo;
    private final ParallelMapper mapper;

    public ParallelRepositoryAdapter(
            JpaParallelRepository parallelRepo,
            JpaCourseRepository courseRepo,
            ParallelMapper mapper) {
        this.parallelRepo = parallelRepo;
        this.courseRepo = courseRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Parallel save(Parallel parallel) {
        ParallelEntity e =
                parallel.id() == null
                        ? new ParallelEntity()
                        : parallelRepo.findById(parallel.id()).orElseGet(ParallelEntity::new);
        e.setName(parallel.name());
        if (parallel.id() != null) e.setId(parallel.id());
        return mapper.toDomain(parallelRepo.save(e));
    }

    @Override
    public Optional<Parallel> findById(Integer id) {
        return parallelRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return parallelRepo.existsByName(name);
    }

    @Override
    public boolean hasClassGroups(Integer parallelId) {
        return courseRepo.existsByParallel_Id(parallelId);
    }

    @Override
    public PageResult<Parallel> list(PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        return SpringPaging.toPageResult(parallelRepo.findAll(pageable).map(mapper::toDomain));
    }

    @Override
    @Transactional
    public void deleteById(Integer id) {
        parallelRepo.deleteById(id);
    }
}
