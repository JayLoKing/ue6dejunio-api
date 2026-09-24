package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.CreateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UpdateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UsersList;
import java.util.UUID;

public interface IUserService {
    User create(CreateUserCommand command);

    User update(UUID id, UpdateUserCommand command);

    User getById(UUID id);

    PageResult<UsersList> list(PageQuery pageQuery, String search, UUID excludeUserId);

    void deactivate(UUID id);

    User activate(UUID id);
}
