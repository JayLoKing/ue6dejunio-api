package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.EvaluationCriterionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CriterionMapper {

    /**
     * The plan is optional and most criteria have none — a teacher writes them for their own class
     * group, and only some answer to a month's plan.
     */
    @Mapping(target = "classGroupId", source = "classGroup.id")
    @Mapping(target = "curriculumPlanId", source = "curriculumPlan.id")
    EvaluationCriterion toDomain(EvaluationCriterionEntity entity);
}
