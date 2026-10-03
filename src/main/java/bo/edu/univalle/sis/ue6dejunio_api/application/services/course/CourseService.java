package bo.edu.univalle.sis.ue6dejunio_api.application.services.course;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CourseWithSubjects;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CreateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.UpdateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService implements ICourseService {

    /** How many courses one read brings back while a whole-school report walks the gestión. */
    private static final int COURSE_PAGE_SIZE = 200;

    private final ICourseDomain courseDomain;
    private final IAcademicYearDomain academicYearDomain;
    private final IClassGroupService classGroupService;
    private final IClassGroupDomain classGroupDomain;
    private final ClosedYearGuard closedYearGuard;

    public CourseService(
            ICourseDomain courseDomain,
            IAcademicYearDomain academicYearDomain,
            IClassGroupService classGroupService,
            IClassGroupDomain classGroupDomain,
            ClosedYearGuard closedYearGuard) {
        this.courseDomain = courseDomain;
        this.academicYearDomain = academicYearDomain;
        this.classGroupService = classGroupService;
        this.classGroupDomain = classGroupDomain;
        this.closedYearGuard = closedYearGuard;
    }

    /**
     * Unguarded on purpose: a course is always created in the active year, and closing a year
     * deactivates it, so the year it lands in can never be a closed one.
     */
    @Override
    @Transactional
    public CourseWithSubjects create(CreateCourseCommand c) {
        if (!courseDomain.gradeExists(c.gradeId())) {
            throw new ResourceNotFoundException("Grado", c.gradeId());
        }
        if (!courseDomain.parallelExists(c.parallelId())) {
            throw new ResourceNotFoundException("Paralelo", c.parallelId());
        }
        Integer yearId = academicYearDomain.currentYearId();
        if (courseDomain.existsByGradeParallelYear(c.gradeId(), c.parallelId(), yearId)) {
            throw new DuplicateResourceException(
                    "curso (grado+paralelo+anio)",
                    c.gradeId() + "/" + c.parallelId() + "/" + yearId);
        }
        if (c.homeroomTeacherId() != null
                && !courseDomain.userIsNonTechnicalTeacher(c.homeroomTeacherId())) {
            throw new ConflictException("El docente de aula debe ser Teacher NO tecnico");
        }
        if (c.homeroomTeacherId() != null) {
            requireNotHomeroomElsewhere(c.homeroomTeacherId(), yearId, null);
        }

        Course course =
                courseDomain.create(c.gradeId(), c.parallelId(), yearId, c.homeroomTeacherId());

        List<ClassGroup> classGroups = List.of();
        if (c.assignments() != null && !c.assignments().isEmpty()) {
            List<CreateClassGroupCommand.Assignment> assignments =
                    c.assignments().stream()
                            .map(
                                    a ->
                                            new CreateClassGroupCommand.Assignment(
                                                    a.subjectId(), a.teacherId()))
                            .toList();
            classGroups =
                    classGroupService.createForCourse(
                            new CreateClassGroupCommand(course.id(), assignments));
        }
        return new CourseWithSubjects(course, classGroups);
    }

    @Override
    @Transactional
    public Course update(UUID id, UpdateCourseCommand c) {
        getById(id);
        closedYearGuard.requireOpenForCourse(id);
        Course result = null;
        if (c.homeroomTeacherId() != null) {
            result = assignHomeroomTeacher(id, c.homeroomTeacherId());
        }
        if (c.active() != null) {
            if (!c.active()) {
                requireNotInUse(id);
            } else {
                requireReactivationKeepsHomeroomUnique(id);
            }
            result = courseDomain.setActive(id, c.active());
        }
        return result != null ? result : getById(id);
    }

    @Override
    @Transactional
    public Course setHomeroomTeacher(UUID id, UUID teacherId) {
        closedYearGuard.requireOpenForCourse(id);
        return assignHomeroomTeacher(id, teacherId);
    }

    /**
     * Only one teacher can be homeroom of a course at a time. The school's real process is: when a
     * teacher goes on leave or does not come back, the Director deactivates their account in
     * Usuarios, creates a new user for the substitute, and only then assigns that new user here.
     * Deactivation is never a side effect of this call — it stays the Director's own, separate
     * decision in Usuarios.
     *
     * <p>So a reassignment away from the current homeroom teacher is refused while that teacher's
     * account is still active, unless the incoming id is that same person (a no-op re-save must not
     * be blocked by its own rule).
     *
     * <p>Once a genuine change of person is allowed through, every class group of this course that
     * the outgoing teacher held moves to the incoming one — "todas las que dictaba el anterior
     * docente". Otherwise the incoming homeroom teacher would see all nine subjects (the homeroom
     * read rule) but be able to write to none of them (the class-group teacher still points at
     * whoever left).
     *
     * <p>A teacher who is already homeroom of a different course in the same gestión is refused
     * here with a readable message; the partial unique index of V24 is the backstop behind it,
     * answering only a generic 409. A course of another gestión does not count: the same person
     * runs a classroom in 2026 and another in 2027. That case is not an error on its own: it is
     * exactly what {@link #swapHomeroomTeachers} exists for, and this message points there.
     */
    private Course assignHomeroomTeacher(UUID id, UUID teacherId) {
        Course current = getById(id);
        if (!courseDomain.userIsNonTechnicalTeacher(teacherId)) {
            throw new ConflictException("El docente de aula debe ser Teacher NO tecnico");
        }
        requireNotHomeroomElsewhere(teacherId, current.academicYearId(), id);
        UUID outgoingTeacherId = current.homeroomTeacherId();
        if (outgoingTeacherId != null
                && current.homeroomTeacherActive()
                && !outgoingTeacherId.equals(teacherId)) {
            throw new ConflictException(
                    current.homeroomTeacherName()
                            + " sigue activo como docente de aula de este curso. Para"
                            + " reasignarlo, primero dale de baja en Usuarios.");
        }
        Course updated = courseDomain.setHomeroomTeacher(id, teacherId);
        if (outgoingTeacherId != null && !outgoingTeacherId.equals(teacherId)) {
            classGroupDomain.reassignTeacherInCourse(id, outgoingTeacherId, teacherId);
        }
        return updated;
    }

    /**
     * One teacher, one active course per gestión. {@code exceptCourseId} is the course being
     * written (null on create, where none exists yet), so re-saving a course's own teacher is not
     * refused by itself.
     */
    private void requireNotHomeroomElsewhere(
            UUID teacherId, Integer academicYearId, UUID exceptCourseId) {
        Optional<Course> elsewhere = courseDomain.homeroomCourseOf(teacherId, academicYearId);
        if (elsewhere.isPresent() && !elsewhere.get().id().equals(exceptCourseId)) {
            throw new ConflictException(
                    "Ya es docente de aula de "
                            + courseLabel(elsewhere.get())
                            + ". Para moverlo aqui usa el intercambio de docentes de aula entre"
                            + " cursos.");
        }
    }

    /**
     * See {@link ICourseService#swapHomeroomTeachers} for the rules. The (teacher, gestión) unique
     * index is checked per statement, so the two homeroom writes cannot simply run back to back:
     * the first would give a teacher a second active course for an instant. Course A is vacated
     * first, then B takes A's teacher, then A takes B's former one — no step ever holds a
     * duplicate, and the use case transaction makes the three one change. The order the two {@link
     * IClassGroupDomain#reassignTeacherInCourse} calls run in is safe precisely because each is
     * scoped to one course: the first call only touches class groups of {@code courseAId}, so by
     * the time the second call runs against {@code courseBId} there is nothing from the first move
     * for it to pick up. Swapping the roles of A and B here would not change that — the two calls
     * never share a course id.
     */
    @Override
    @Transactional
    public Course swapHomeroomTeachers(UUID courseAId, UUID courseBId) {
        if (courseAId.equals(courseBId)) {
            throw new ConflictException(
                    "No se puede intercambiar el docente de aula de un curso consigo mismo.");
        }
        Course courseA = getById(courseAId);
        Course courseB = getById(courseBId);
        closedYearGuard.requireOpenForCourses(List.of(courseAId, courseBId));

        UUID teacherA = courseA.homeroomTeacherId();
        UUID teacherB = courseB.homeroomTeacherId();
        if (teacherA == null) {
            throw new ConflictException(
                    courseLabel(courseA)
                            + " no tiene un docente de aula asignado; nada que"
                            + " intercambiar.");
        }
        if (teacherB == null) {
            throw new ConflictException(
                    courseLabel(courseB)
                            + " no tiene un docente de aula asignado; nada que"
                            + " intercambiar.");
        }
        if (!courseA.homeroomTeacherActive()) {
            throw new ConflictException(
                    courseA.homeroomTeacherName()
                            + " (docente de aula de "
                            + courseLabel(courseA)
                            + ") esta dado de baja; esto no es un intercambio. Usa la"
                            + " reasignacion normal.");
        }
        if (!courseB.homeroomTeacherActive()) {
            throw new ConflictException(
                    courseB.homeroomTeacherName()
                            + " (docente de aula de "
                            + courseLabel(courseB)
                            + ") esta dado de baja; esto no es un intercambio. Usa la"
                            + " reasignacion normal.");
        }

        courseDomain.clearHomeroomTeacher(courseAId);
        courseDomain.setHomeroomTeacher(courseBId, teacherA);
        Course updatedA = courseDomain.setHomeroomTeacher(courseAId, teacherB);

        classGroupDomain.reassignTeacherInCourse(courseAId, teacherA, teacherB);
        classGroupDomain.reassignTeacherInCourse(courseBId, teacherB, teacherA);

        return updatedA;
    }

    /**
     * An inactive course may keep its homeroom teacher while that teacher takes another course in
     * the same gestión. Switching the dormant one back on would then make them homeroom of two, so
     * it is refused here with the same message instead of surfacing as the index's generic 409.
     */
    private void requireReactivationKeepsHomeroomUnique(UUID courseId) {
        Course course = getById(courseId);
        if (!course.active() && course.homeroomTeacherId() != null) {
            requireNotHomeroomElsewhere(
                    course.homeroomTeacherId(), course.academicYearId(), courseId);
        }
    }

    private String courseLabel(Course course) {
        return course.gradeName() + " " + course.parallelName();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Course> homeroomCourseOf(UUID teacherId) {
        return courseDomain.homeroomCourseOf(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public Course getById(UUID id) {
        return courseDomain
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Course> list(Integer academicYearId, PageQuery pageQuery) {
        return courseDomain.list(academicYearId, pageQuery);
    }

    /**
     * No sort is asked for because the listing already carries one: it orders by grade and
     * parallel, and within a single gestión that pair is unique, so no row can appear on two pages
     * or on none. Across gestiones the pair ties, which is why every whole-school report requires
     * one.
     *
     * <p>The loop asks how many rows are still missing rather than how many pages the store
     * reports, because {@code totalPages} is derived from the size that was asked for and answers
     * one for any set that fits in a single page — which is every set, until it is not. The empty
     * page is the other exit: a store that keeps answering nothing would otherwise be read forever.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Course> allOfYear(Integer academicYearId) {
        List<Course> all = new ArrayList<>();
        int pageIndex = 0;
        while (true) {
            PageResult<Course> page =
                    courseDomain.list(academicYearId, PageQuery.of(pageIndex, COURSE_PAGE_SIZE));
            all.addAll(page.content());
            if (page.content().isEmpty() || all.size() >= page.totalElements()) {
                return all;
            }
            pageIndex++;
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        closedYearGuard.requireOpenForCourse(id);
        requireNotInUse(id);
        courseDomain.setActive(id, false);
    }

    /**
     * A course that holds students or teacher-authored content is history, not a mistake to take
     * back. Bare class groups do not count: duplicating a gestión creates them for every course.
     */
    private void requireNotInUse(UUID id) {
        if (courseDomain.hasEnrollments(id)) {
            throw new ConflictException(
                    "No se puede dar de baja el curso: tiene estudiantes inscritos");
        }
        if (courseDomain.hasAcademicRecords(id)) {
            throw new ConflictException(
                    "No se puede dar de baja el curso: tiene registros académicos");
        }
    }
}
