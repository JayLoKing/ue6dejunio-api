package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UsersList;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers.UserMapper;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import jakarta.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class UserRepositoryAdapter implements IUserDomain {

    private final JpaUserRepository repo;
    private final UserMapper mapper;

    public UserRepositoryAdapter(JpaUserRepository repo, UserMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Override
    public PageResult<UsersList> getUsers(
            PageQuery pageQuery, @Nullable String search, UUID excludeUserId) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        if (search == null || search.isBlank()) {
            return SpringPaging.toPageResult(
                    repo.listExcluding(excludeUserId, pageable).map(mapper::toListItem));
        }
        return SpringPaging.toPageResult(
                repo.search(search, excludeUserId, pageable).map(mapper::toListItem));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repo.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repo.existsByEmail(email);
    }

    @Override
    public boolean existsByCi(String ci) {
        return repo.existsByCi(ci);
    }

    @Override
    @Transactional
    public User save(User user) {
        UserEntity entity = mapper.toEntity(user);
        return mapper.toDomain(repo.save(entity));
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        UserEntity entity =
                repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        entity.setActive(false);
        repo.save(entity);
    }
}
