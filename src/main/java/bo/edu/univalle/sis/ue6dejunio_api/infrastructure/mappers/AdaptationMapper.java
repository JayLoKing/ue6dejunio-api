package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumAdaptationEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface AdaptationMapper {

    /**
     * The student's name travels with the row so the wizard's table can be read without asking for
     * the roster. Everything else is a copy or an id.
     */
    @Mapping(target = "planId", source = "curriculumPlan.id")
    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "studentName", source = "student", qualifiedByName = "studentFullName")
    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "updatedById", source = "updatedBy.id")
    Adaptation toDomain(CurriculumAdaptationEntity entity);

    /**
     * The only field here that is neither a copy nor an id: two columns joined. With no student
     * there is no name, rather than the "null null" a careless join writes.
     */
    @Named("studentFullName")
    default String studentFullName(StudentEntity student) {
        return student == null ? null : student.getNames() + " " + student.getLastNames();
    }
}
