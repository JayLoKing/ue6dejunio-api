package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.parallel.IParallelDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ParallelEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaParallelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class ParallelRepositoryAdapter implements IParallelDomain {

    private final JpaParallelRepository parallelRepo;
    private final JpaClassGroupRepository classGroupRepo;

    public ParallelRepositoryAdapter(JpaParallelRepository parallelRepo,
                                     JpaClassGroupRepository classGroupRepo) {
        this.parallelRepo = parallelRepo;
        this.classGroupRepo = classGroupRepo;
    }

    @Override
    @Transactional
    public Parallel save(Parallel parallel) {
        ParallelEntity e = parallel.id() == null
            ? new ParallelEntity()
            : parallelRepo.findById(parallel.id()).orElseGet(ParallelEntity::new);
        e.setName(parallel.name());
        if (parallel.id() != null) e.setId(parallel.id());
        return toDomain(parallelRepo.save(e));
    }

    @Override
    public Optional<Parallel> findById(Integer id) {
        return parallelRepo.findById(id).map(this::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return parallelRepo.existsByName(name);
    }

    @Override
    public boolean hasClassGroups(Integer parallelId) {
        return classGroupRepo.existsByParallel_Id(parallelId);
    }

    @Override
    public Page<Parallel> list(Pageable pageable) {
        return parallelRepo.findAll(pageable).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteById(Integer id) {
        parallelRepo.deleteById(id);
    }

    private Parallel toDomain(ParallelEntity e) {
        return new Parallel(e.getId(), e.getName());
    }
}
