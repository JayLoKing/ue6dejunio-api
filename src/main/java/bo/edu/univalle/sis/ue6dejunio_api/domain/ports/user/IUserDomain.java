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

    /**
     * Whether this user still has an open account, asked once per authenticated request.
     *
     * <p>Its own method rather than {@code findById(id).map(User::isActive)}: this runs on every
     * request that carries a token, and the user entity pulls its role eagerly and carries the
     * password hash. One boolean off an indexed lookup is what the question deserves.
     *
     * <p>Answers {@code false} for a user that does not exist. The caller is deciding whether to
     * let a request through, and "no such account" and "closed account" both mean no — telling them
     * apart would only hand a caller a way to probe which ids exist.
     */
    boolean isActive(UUID id);

    boolean existsByEmail(String email);

    boolean existsByCi(String ci);

    User save(User user);

    void deactivate(UUID id);
}
