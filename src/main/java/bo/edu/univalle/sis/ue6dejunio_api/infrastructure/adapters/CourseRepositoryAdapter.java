package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicYearEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAcademicYearRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaGradeRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaParallelRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CourseRepositoryAdapter implements ICourseDomain {

    private static final String TEACHER_ROLE = "Teacher";

    private final JpaCourseRepository courseRepo;
    private final JpaGradeRepository gradeRepo;
    private final JpaParallelRepository parallelRepo;
    private final JpaAcademicYearRepository yearRepo;
    private final JpaUserRepository userRepo;

    public CourseRepositoryAdapter(
            JpaCourseRepository courseRepo,
            JpaGradeRepository gradeRepo,
            JpaParallelRepository parallelRepo,
            JpaAcademicYearRepository yearRepo,
            JpaUserRepository userRepo) {
        this.courseRepo = courseRepo;
        this.gradeRepo = gradeRepo;
        this.parallelRepo = parallelRepo;
        this.yearRepo = yearRepo;
        this.userRepo = userRepo;
    }

    @Override
    public boolean gradeExists(Integer gradeId) {
        return gradeRepo.existsById(gradeId);
    }

    @Override
    public boolean parallelExists(Integer parallelId) {
        return parallelRepo.existsById(parallelId);
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
    public Integer currentAcademicYearId() {
        return yearRepo.findTopByOrderByYearDesc()
                .map(AcademicYearEntity::getId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", "actual"));
    }

    @Override
    public boolean existsByGradeParallelYear(
            Integer gradeId, Integer parallelId, Integer academicYearId) {
        return courseRepo.existsByGrade_IdAndParallel_IdAndAcademicYear_Id(
                gradeId, parallelId, academicYearId);
    }

    @Override
    @Transactional
    public Course create(
            Integer gradeId, Integer parallelId, Integer academicYearId, UUID homeroomTeacherId) {
        CourseEntity e = new CourseEntity();
        e.setGrade(gradeRepo.getReferenceById(gradeId));
        e.setParallel(parallelRepo.getReferenceById(parallelId));
        e.setAcademicYear(yearRepo.getReferenceById(academicYearId));
        if (homeroomTeacherId != null) {
            e.setHomeroomTeacher(userRepo.getReferenceById(homeroomTeacherId));
        }
        e.setActive(true);
        return toDomain(courseRepo.save(e));
    }

    @Override
    @Transactional
    public Course setHomeroomTeacher(UUID courseId, UUID teacherId) {
        CourseEntity e = load(courseId);
        UserEntity teacher =
                userRepo.findById(teacherId)
                        .orElseThrow(() -> new ResourceNotFoundException("Docente", teacherId));
        e.setHomeroomTeacher(teacher);
        return toDomain(courseRepo.save(e));
    }

    @Override
    @Transactional
    public Course setActive(UUID courseId, boolean active) {
        CourseEntity e = load(courseId);
        e.setActive(active);
        return toDomain(courseRepo.save(e));
    }

    @Override
    public Optional<Course> findById(UUID id) {
        return courseRepo.findById(id).map(this::toDomain);
    }

    @Override
    public boolean isHomeroomTeacherOfAny(UUID teacherId, Collection<UUID> courseIds) {
        // An empty IN is invalid SQL on some dialects and a pointless query on all of them.
        if (teacherId == null || courseIds == null || courseIds.isEmpty()) {
            return false;
        }
        return courseRepo.existsByHomeroomTeacher_IdAndIdIn(teacherId, courseIds);
    }

    @Override
    public boolean isHomeroomTeacherOfAll(UUID teacherId, Collection<UUID> courseIds) {
        // An empty IN is invalid SQL on some dialects and a pointless query on all of them.
        if (teacherId == null || courseIds == null || courseIds.isEmpty()) {
            return false;
        }
        Set<UUID> distinct = new HashSet<>(courseIds);
        // Counting rather than fetching also answers for ids that resolve to nothing: they are
        // never counted, so a batch spanning a stale course id is refused as a whole.
        return courseRepo.countByHomeroomTeacher_IdAndIdIn(teacherId, distinct) == distinct.size();
    }

    @Override
    public PageResult<Course> list(Integer academicYearId, PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        return SpringPaging.toPageResult(
                courseRepo.search(academicYearId, pageable).map(this::toDomain));
    }

    @Override
    public Optional<Course> homeroomCourseOf(UUID teacherId) {
        return courseRepo
                .findFirstByHomeroomTeacher_IdAndActiveTrueOrderByAcademicYear_YearDesc(teacherId)
                .map(this::toDomain);
    }

    private CourseEntity load(UUID id) {
        return courseRepo
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id));
    }

    private Course toDomain(CourseEntity e) {
        UserEntity ht = e.getHomeroomTeacher();
        return new Course(
                e.getId(),
                e.getGrade().getId(),
                e.getGrade().getName(),
                e.getParallel().getId(),
                e.getParallel().getName(),
                e.getAcademicYear().getId(),
                e.getAcademicYear().getYear(),
                ht != null ? ht.getId() : null,
                ht != null ? ht.getNames() + " " + ht.getLastNames() : null,
                e.isActive());
    }
}
