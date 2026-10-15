package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.CreateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UpdateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UserUpdate;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UsersList;
import java.util.Optional;
import java.util.UUID;

public interface IUserService {
    User create(CreateUserCommand command);

    /**
     * Applies the Director's edit. A new email, CI or phone is checked for an owner like on create,
     * and only when it changed. If the user never got past the temporary password, a new one is
     * mailed to the corrected address: the first one most likely went to the typo.
     *
     * @throws bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException if
     *     the new email, CI or phone already belongs to another user
     * @throws bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException if the email
     *     or the CI is blank, or the role asked for is Director
     */
    UserUpdate update(UUID id, UpdateUserCommand command);

    User getById(UUID id);

    /**
     * Who owns this email address, if anybody. The Director's handover form asks before it posts,
     * because posting an address that already belongs to someone promotes that person on the spot.
     * The address is trimmed; an unknown one is an empty answer, not an error.
     */
    Optional<User> findByEmail(String email);

    PageResult<UsersList> list(PageQuery pageQuery, String search, UUID excludeUserId);

    void deactivate(UUID id);

    User activate(UUID id);

    /**
     * Mints a new temporary password for an account whose owner never got past the first one, and
     * mails it. Refused once the user has set a password of their own: from then on a reset goes
     * through the forgot-password flow, not through the Director.
     *
     * @throws bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException if the
     *     user does not exist
     * @throws bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException if the account
     *     is closed or the user already changed the temporary password
     */
    void resendCredentials(UUID id);
}
