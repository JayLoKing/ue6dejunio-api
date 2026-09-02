package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.SubjectEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubjectMapper {

    @Mapping(target = "areaId", source = "area.id")
    @Mapping(target = "areaName", source = "area.name")
    Subject toDomain(SubjectEntity entity);
}
