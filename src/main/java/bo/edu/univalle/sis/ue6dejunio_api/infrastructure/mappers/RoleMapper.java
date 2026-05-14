package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.RoleEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    Role toDomain(RoleEntity entity);
    RoleEntity toEntity(Role domain);
}
