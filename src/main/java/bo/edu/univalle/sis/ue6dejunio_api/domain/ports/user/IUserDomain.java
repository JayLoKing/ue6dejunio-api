package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UsersList;

import java.util.Optional;
import java.util.UUID;

public interface IUserDomain {
    PageResult<UsersList> getUsers(PageQuery pageQuery, String search, UUID excludeUserId);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByCi(String ci);
    User save(User user);
    void deactivate(UUID id);
}
