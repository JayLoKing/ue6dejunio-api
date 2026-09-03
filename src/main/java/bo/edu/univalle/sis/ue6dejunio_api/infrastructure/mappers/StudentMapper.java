package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface StudentMapper {

    /**
     * Who changed the status travels as an id and as a name. The teacher reading that one of their
     * students is gone needs somebody to ask, and a UUID is not somebody.
     */
    @Mapping(target = "statusChangedById", source = "statusChangedBy.id")
    @Mapping(target = "statusChangedByName", source = "statusChangedBy", qualifiedByName = "userFullName")
    Student toDomain(StudentEntity entity);

    /**
     * Why the status change is not mapped back.
     *
     * <p>{@code status} stays: creating a student is what sets the first one, and the enrolment
     * import passes it. Everything that explains a change does not — the category, the words and
     * the author belong to {@code updateStatus}, which is the only call that has an actor to
     * record. Mapped here, an edit that merely corrects a surname would carry whatever the caller
     * happened to hold and leave the row saying it is active while still naming who took the
     * student off the roll.
     */
    @Mapping(target = "statusReason", ignore = true)
    @Mapping(target = "statusNote", ignore = true)
    @Mapping(target = "statusChangedAt", ignore = true)
    @Mapping(target = "statusChangedBy", ignore = true)
    StudentEntity toEntity(Student domain);

    /** No user, no name — rather than the "null null" a careless join writes. */
    @Named("userFullName")
    default String userFullName(UserEntity user) {
        return user == null ? null : user.getNames() + " " + user.getLastNames();
    }
}
