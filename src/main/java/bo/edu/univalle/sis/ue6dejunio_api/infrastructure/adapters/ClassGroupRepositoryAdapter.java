package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroupField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ClassGroupEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.SubjectEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaSubjectRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class ClassGroupRepositoryAdapter implements IClassGroupDomain {

    private static final String TEACHER_ROLE = "Teacher";

    private final JpaClassGroupRepository classGroupRepo;
    private final JpaCourseRepository courseRepo;
    private final JpaSubjectRepository subjectRepo;
    private final JpaUserRepository userRepo;

    public ClassGroupRepositoryAdapter(
            JpaClassGroupRepository classGroupRepo,
            JpaCourseRepository courseRepo,
            JpaSubjectRepository subjectRepo,
            JpaUserRepository userRepo) {
        this.classGroupRepo = classGroupRepo;
        this.courseRepo = courseRepo;
        this.subjectRepo = subjectRepo;
        this.userRepo = userRepo;
    }

    @Override
    public boolean courseExists(UUID courseId) {
        return courseRepo.existsById(courseId);
    }

    @Override
    public boolean subjectExists(UUID subjectId) {
        return subjectRepo.existsById(subjectId);
    }

    @Override
    public boolean userIsTeacher(UUID userId) {
        return userRepo.findById(userId)
                .map(u -> u.getRole() != null && TEACHER_ROLE.equals(u.getRole().getName()))
                .orElse(false);
    }

    @Override
    public boolean userIsTechnicalTeacher(UUID userId) {
        return userRepo.findById(userId)
                .map(
                        u ->
                                u.getRole() != null
                                        && TEACHER_ROLE.equals(u.getRole().getName())
                                        && u.isTechnical())
                .orElse(false);
    }

    @Override
    public boolean userIsNonTechnicalTeacher(UUID userId) {
        return userRepo.findById(userId)
                .map(
                        u ->
                                u.getRole() != null
                                        && TEACHER_ROLE.equals(u.getRole().getName())
                                        && !u.isTechnical())
                .orElse(false);
    }

    @Override
    public boolean userIsHomeroomTeacherOf(UUID userId, UUID courseId) {
        if (userId == null || courseId == null) {
            return false;
        }
        return courseRepo
                .findById(courseId)
                .map(CourseEntity::getHomeroomTeacher)
                .map(t -> userId.equals(t.getId()))
                .orElse(false);
    }

    @Override
    public boolean subjectIsTechnical(UUID subjectId) {
        return subjectRepo.findById(subjectId).map(s -> s.isTechnical()).orElse(false);
    }

    @Override
    public boolean existsByCourseAndSubject(UUID courseId, UUID subjectId) {
        return classGroupRepo.existsByCourse_IdAndSubject_Id(courseId, subjectId);
    }

    @Override
    public boolean teachesInCourse(UUID teacherId, UUID courseId) {
        return classGroupRepo.existsByCourse_IdAndTeacher_Id(courseId, teacherId);
    }

    @Override
    public boolean teachesInAnyCourse(UUID teacherId, Collection<UUID> courseIds) {
        // An empty IN is invalid SQL on some dialects and a pointless query on all of them.
        if (teacherId == null || courseIds == null || courseIds.isEmpty()) {
            return false;
        }
        return classGroupRepo.existsByTeacher_IdAndCourse_IdIn(teacherId, courseIds);
    }

    @Override
    @Transactional
    public ClassGroup create(UUID courseId, UUID subjectId, UUID teacherId) {
        CourseEntity course =
                courseRepo
                        .findById(courseId)
                        .orElseThrow(() -> new ResourceNotFoundException("Curso", courseId));
        SubjectEntity subject =
                subjectRepo
                        .findById(subjectId)
                        .orElseThrow(() -> new ResourceNotFoundException("Materia", subjectId));
        ClassGroupEntity e = new ClassGroupEntity();
        e.setCourse(course);
        e.setSubject(subject);
        if (teacherId != null) {
            e.setTeacher(userRepo.getReferenceById(teacherId));
        }
        e.setActive(true);
        return toDomain(classGroupRepo.save(e));
    }

    @Override
    public Optional<ClassGroup> findById(UUID id) {
        return classGroupRepo.findById(id).map(this::toDomain);
    }

    /**
     * {@code findByIdIn} and not {@code findAllById}: the to-one associations are EAGER on the
     * entity and a plain lookup does not join-fetch them, so Hibernate would resolve the course,
     * its grade and parallel, the subject and the teacher with a follow-up select each, per row.
     * The named finder carries the graph that keeps it one query.
     */
    @Override
    public List<ClassGroup> findByIdIn(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return classGroupRepo.findByIdIn(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ClassGroup> byCourse(UUID courseId) {
        return classGroupRepo.findByCourse_IdOrderBySubject_Name(courseId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ClassGroupField> knowledgeFieldsByCourse(UUID courseId) {
        // The plan-order query already fetches subject.area through an entity graph, so this is one
        // query and not one per subject — every to-one on this entity is EAGER, and reaching the
        // area through a plain findAll would be the N+1 that graph exists to prevent.
        return classGroupRepo.findActiveOfCourseInPlanOrder(courseId).stream()
                .map(
                        cg ->
                                new ClassGroupField(
                                        cg.getId(),
                                        cg.getSubject().getArea().getId(),
                                        cg.getSubject().getArea().getName(),
                                        cg.getSubject().getArea().getDisplayOrder()))
                .toList();
    }

    @Override
    public List<ClassGroup> byTeacher(UUID teacherId) {
        return classGroupRepo.findByTeacher_IdOrderBySubject_Name(teacherId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public UUID courseIdOfClassGroup(UUID classGroupId) {
        return classGroupRepo
                .findById(classGroupId)
                .map(cg -> cg.getCourse().getId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Materia del curso", classGroupId));
    }

    @Override
    public UUID teacherIdOfClassGroup(UUID classGroupId) {
        return classGroupRepo
                .findById(classGroupId)
                .map(cg -> cg.getTeacher() != null ? cg.getTeacher().getId() : null)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Materia del curso", classGroupId));
    }

    @Override
    @Transactional
    public void setActive(UUID classGroupId, boolean active) {
        ClassGroupEntity e =
                classGroupRepo
                        .findById(classGroupId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Materia del curso", classGroupId));
        e.setActive(active);
        classGroupRepo.save(e);
    }

    @Override
    @Transactional
    public ClassGroup setTeacher(UUID classGroupId, UUID teacherId) {
        ClassGroupEntity e =
                classGroupRepo
                        .findById(classGroupId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Materia del curso", classGroupId));
        UserEntity teacher =
                userRepo.findById(teacherId)
                        .orElseThrow(() -> new ResourceNotFoundException("Docente", teacherId));
        e.setTeacher(teacher);
        return toDomain(classGroupRepo.save(e));
    }

    private ClassGroup toDomain(ClassGroupEntity e) {
        CourseEntity c = e.getCourse();
        UserEntity t = e.getTeacher();
        return new ClassGroup(
                e.getId(),
                c != null ? c.getId() : null,
                c != null && c.getGrade() != null ? c.getGrade().getName() : null,
                c != null && c.getParallel() != null ? c.getParallel().getName() : null,
                e.getSubject().getId(),
                e.getSubject().getName(),
                t != null ? t.getId() : null,
                t != null ? t.getNames() + " " + t.getLastNames() : null,
                e.isActive());
    }
}
