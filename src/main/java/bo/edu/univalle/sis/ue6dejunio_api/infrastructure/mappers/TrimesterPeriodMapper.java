package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.TrimesterPeriodEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TrimesterPeriodMapper {

    @Mapping(target = "academicYearId", source = "academicYear.id")
    TrimesterPeriod toDomain(TrimesterPeriodEntity entity);
}
