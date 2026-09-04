package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IClassGroupDomain {
    boolean courseExists(UUID courseId);
    boolean subjectExists(UUID subjectId);
    boolean userIsTeacher(UUID userId);
    boolean userIsTechnicalTeacher(UUID userId);
    boolean userIsNonTechnicalTeacher(UUID userId);

    /**
     * Whether this teacher is the one who runs the course — its homeroom teacher.
     *
     * <p>Asked about one course and not in general: the school lets the teacher in charge of a
     * course take its technical subjects when no technical teacher is free, and that licence is
     * theirs over their own course only.
     */
    boolean userIsHomeroomTeacherOf(UUID userId, UUID courseId);
    boolean subjectIsTechnical(UUID subjectId);
    boolean existsByCourseAndSubject(UUID courseId, UUID subjectId);

    /** Whether the teacher runs at least one class group in the course. A technical teacher has no
     * homeroom, so this is what ties them to the course's roster. */
    boolean teachesInCourse(UUID teacherId, UUID courseId);

    /**
     * Same tie as {@link #teachesInCourse}, asked once for a whole set of courses. An ownership
     * guard spanning every course a student is enrolled in must not query them one by one.
     */
    boolean teachesInAnyCourse(UUID teacherId, Collection<UUID> courseIds);

    ClassGroup create(UUID courseId, UUID subjectId, UUID teacherId);
    Optional<ClassGroup> findById(UUID id);
    List<ClassGroup> byCourse(UUID courseId);
    List<ClassGroup> byTeacher(UUID teacherId);
    UUID courseIdOfClassGroup(UUID classGroupId);
    UUID teacherIdOfClassGroup(UUID classGroupId);
    void setActive(UUID classGroupId, boolean active);
    ClassGroup setTeacher(UUID classGroupId, UUID teacherId);
}
