package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UsersList;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface IUserDomain {
    Page<UsersList> getUsers(Pageable pageable, @Nullable String search, java.util.UUID excludeUserId);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByCi(String ci);
    User save(User user);
    void deactivate(UUID id);
}
