package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.Grade;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.GradeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GradeMapper {

    /**
     * The level travels flattened onto the grade. MapStruct guards the walk, so a grade whose level
     * row is gone reads as a grade without one rather than reaching a getter on null.
     */
    @Mapping(target = "levelId", source = "level.id")
    @Mapping(target = "levelName", source = "level.name")
    Grade toDomain(GradeEntity entity);
}
