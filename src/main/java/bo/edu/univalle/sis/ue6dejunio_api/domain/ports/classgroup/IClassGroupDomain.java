package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IClassGroupDomain {
    boolean courseExists(UUID courseId);
    boolean subjectExists(UUID subjectId);
    boolean userIsTeacher(UUID userId);
    boolean userIsTechnicalTeacher(UUID userId);
    boolean userIsNonTechnicalTeacher(UUID userId);
    boolean subjectIsTechnical(UUID subjectId);
    boolean existsByCourseAndSubject(UUID courseId, UUID subjectId);
    ClassGroup create(UUID courseId, UUID subjectId, UUID teacherId);
    Optional<ClassGroup> findById(UUID id);
    List<UUID> classGroupIdsByCourse(UUID courseId);
    List<ClassGroup> byCourse(UUID courseId);
    List<ClassGroup> byTeacher(UUID teacherId);
    UUID courseIdOfClassGroup(UUID classGroupId);
    UUID teacherIdOfClassGroup(UUID classGroupId);
    void setActive(UUID classGroupId, boolean active);
    ClassGroup setTeacher(UUID classGroupId, UUID teacherId);
}
