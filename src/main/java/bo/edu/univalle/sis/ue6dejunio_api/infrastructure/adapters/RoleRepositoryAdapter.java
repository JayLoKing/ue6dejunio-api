package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.role.IRoleDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers.RoleMapper;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaRoleRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class RoleRepositoryAdapter implements IRoleDomain {

    private final JpaRoleRepository repo;
    private final RoleMapper mapper;

    public RoleRepositoryAdapter(JpaRoleRepository repo, RoleMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Override
    public List<Role> findAll() {
        return repo.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Role> findById(Integer id) {
        return repo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Role> findByName(String name) {
        return repo.findByName(name).map(mapper::toDomain);
    }
}
