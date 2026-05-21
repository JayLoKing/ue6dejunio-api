package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StudentMapper {
    Student toDomain(StudentEntity entity);
    StudentEntity toEntity(Student domain);
}
