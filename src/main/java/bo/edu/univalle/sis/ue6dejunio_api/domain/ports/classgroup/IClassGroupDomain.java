package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroupField;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Whether the teacher runs at least one class group in the course. A technical teacher has no
     * homeroom, so this is what ties them to the course's roster.
     */
    boolean teachesInCourse(UUID teacherId, UUID courseId);

    /**
     * The ids of the class groups the teacher runs in the course, in one query. What a panel that
     * spans every subject of a course needs to keep a teacher to their own rows.
     */
    Set<UUID> classGroupIdsTaughtInCourse(UUID teacherId, UUID courseId);

    /**
     * The ids of every class group the teacher may read across a set of courses, in one query: the
     * ones they run, plus every class group of a course they are homeroom teacher of. What a
     * per-student view spanning several courses needs to apply the course rule once per course
     * without asking course by course.
     */
    Set<UUID> classGroupIdsReadableByTeacherInCourses(UUID teacherId, Collection<UUID> courseIds);

    /**
     * Same tie as {@link #teachesInCourse}, asked once for a whole set of courses. An ownership
     * guard spanning every course a student is enrolled in must not query them one by one.
     */
    boolean teachesInAnyCourse(UUID teacherId, Collection<UUID> courseIds);

    ClassGroup create(UUID courseId, UUID subjectId, UUID teacherId);

    /**
     * Opens one active class group per subject of each course, with no teacher, and says how many
     * it wrote. Only for copying a year's structure into another, where the Director names the
     * teachers afterwards; every other way of opening a class group still requires one. Writes by
     * reference, so it reads nothing per row.
     */
    int createUnassigned(Map<UUID, ? extends Collection<UUID>> subjectIdsByCourse);

    /**
     * The subjects each of these courses has an ACTIVE class group for, in one query. A course with
     * none is absent from the map.
     */
    Map<UUID, List<UUID>> activeSubjectIdsByCourse(Collection<UUID> courseIds);

    Optional<ClassGroup> findById(UUID id);

    /**
     * The same lookup for a whole set at once.
     *
     * <p>For the callers that hold a handful of class group ids and need every one of them named —
     * a run announcing its results across the subjects it touched. Asked one at a time that is a
     * query per subject inside a loop.
     *
     * @return only the ones that exist, in no particular order
     */
    List<ClassGroup> findByIdIn(Collection<UUID> ids);

    List<ClassGroup> byCourse(UUID courseId);

    /**
     * The field of knowledge each of a course's class groups belongs to, in one query.
     *
     * <p>For the documents that group the areas under their field — the libreta does, and the
     * centralizer does not. Asked for the course rather than per class group, because a libreta
     * that looked this up per subject would run a query per row it prints.
     */
    List<ClassGroupField> knowledgeFieldsByCourse(UUID courseId);

    List<ClassGroup> byTeacher(UUID teacherId);

    UUID courseIdOfClassGroup(UUID classGroupId);

    UUID teacherIdOfClassGroup(UUID classGroupId);

    void setActive(UUID classGroupId, boolean active);

    ClassGroup setTeacher(UUID classGroupId, UUID teacherId);

    /**
     * Moves every ACTIVE class group of {@code courseId} that {@code fromTeacherId} was teaching
     * over to {@code toTeacherId} — the rule the Director asked for on a homeroom reassignment:
     * "todas las que dictaba el anterior docente". Technical subjects included, if the outgoing
     * homeroom teacher held any in this course.
     *
     * <p>The boundary is exactly two things, and both matter: (1) only the class groups {@code
     * fromTeacherId} held in {@code courseId} move — a subject of the same course held by some
     * third teacher stays with that third teacher, and (2) only within {@code courseId} — a subject
     * {@code fromTeacherId} teaches in another course is untouched.
     *
     * <p>Only ACTIVE class groups move. An inactive one is a subject that was closed; rewriting its
     * teacher would rewrite who taught it, which is a historical fact, not an oversight to fix.
     *
     * @return how many class groups moved, 0 when the outgoing teacher held none in this course
     */
    int reassignTeacherInCourse(UUID courseId, UUID fromTeacherId, UUID toTeacherId);

    /**
     * Erases every class group of the course in one statement, for a course being removed for good.
     * Only for bare class groups: a score on one blocks the delete (its key is RESTRICT), and
     * criteria, attendance or plans on one would be erased with it, which is why the caller first
     * makes sure the course holds no academic record.
     */
    void deleteAllOfCourse(UUID courseId);
}
