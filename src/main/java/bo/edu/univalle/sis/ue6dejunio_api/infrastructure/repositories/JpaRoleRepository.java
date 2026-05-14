package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaRoleRepository extends JpaRepository<RoleEntity, Integer> {
    Optional<RoleEntity> findByName(String name);
}
