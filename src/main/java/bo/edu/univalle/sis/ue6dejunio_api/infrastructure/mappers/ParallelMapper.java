package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ParallelEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ParallelMapper {
    Parallel toDomain(ParallelEntity entity);
}
