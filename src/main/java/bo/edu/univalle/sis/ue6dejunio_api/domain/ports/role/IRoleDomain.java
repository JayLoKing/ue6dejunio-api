package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.role;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import java.util.List;
import java.util.Optional;

public interface IRoleDomain {
    List<Role> findAll();

    Optional<Role> findById(Integer id);

    Optional<Role> findByName(String name);
}
