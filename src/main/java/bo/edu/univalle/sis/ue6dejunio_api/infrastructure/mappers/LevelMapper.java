package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.Level;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.LevelEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LevelMapper {
    Level toDomain(LevelEntity entity);
}
