package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.PlanProgress;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanProgressEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProgressMapper {

    /** The author is already a raw id on the row: the progress note records who, not their record. */
    @Mapping(target = "planId", source = "curriculumPlan.id")
    PlanProgress toDomain(CurriculumPlanProgressEntity entity);
}
