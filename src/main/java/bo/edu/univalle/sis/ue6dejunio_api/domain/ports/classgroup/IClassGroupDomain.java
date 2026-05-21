package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.TeacherHomeroom;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IClassGroupDomain {
    Integer currentAcademicYearId();
    boolean existsAssignment(UUID subjectId, Integer gradeId, Integer parallelId, Integer academicYearId);
    ClassGroup createAssignment(Integer gradeId, Integer parallelId, Integer academicYearId,
                                UUID subjectId, UUID teacherId);
    Optional<TeacherHomeroom> resolveTeacherHomeroom(UUID teacherId);
    List<UUID> classGroupIdsByCourse(Integer gradeId, Integer parallelId, Integer academicYearId);
}
