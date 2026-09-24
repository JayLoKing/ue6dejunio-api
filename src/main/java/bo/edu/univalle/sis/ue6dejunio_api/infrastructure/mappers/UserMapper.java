package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.UsersList;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        uses = {RoleMapper.class})
public interface UserMapper {

    @Mapping(target = "role", source = "role")
    User toDomain(UserEntity entity);

    @Mapping(target = "role", source = "role")
    UserEntity toEntity(User domain);

    @Mapping(
            target = "role",
            expression = "java(entity.getRole() != null ? entity.getRole().getName() : null)")
    UsersList toListItem(UserEntity entity);

    List<UsersList> toListItems(List<UserEntity> entities);
}
