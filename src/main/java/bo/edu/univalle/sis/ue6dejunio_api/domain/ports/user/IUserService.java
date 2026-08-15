package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.CreateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UpdateUserCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UsersList;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IUserService {
    User create(CreateUserCommand command);
    User update(UUID id, UpdateUserCommand command);
    User getById(UUID id);
    Page<UsersList> list(Pageable pageable, @Nullable String search, java.util.UUID excludeUserId);
    void deactivate(UUID id);
    User activate(UUID id);
}
