package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEnrollmentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseEnrollmentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaStudentRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class CourseEnrollmentRepositoryAdapter implements ICourseEnrollmentDomain {

    private static final String STATUS_EFFECTIVE = "Effective";
    private static final String STATUS_WITHDRAWN = "Withdrawn";

    private final JpaCourseEnrollmentRepository enrollmentRepo;
    private final JpaStudentRepository studentRepo;
    private final JpaCourseRepository courseRepo;

    public CourseEnrollmentRepositoryAdapter(JpaCourseEnrollmentRepository enrollmentRepo,
                                             JpaStudentRepository studentRepo,
                                             JpaCourseRepository courseRepo) {
        this.enrollmentRepo = enrollmentRepo;
        this.studentRepo = studentRepo;
        this.courseRepo = courseRepo;
    }

    @Override
    public boolean courseExists(UUID courseId) {
        return courseRepo.existsById(courseId);
    }

    @Override
    public List<UUID> enrolledStudentIds(UUID courseId, Collection<UUID> studentIds) {
        // An empty IN is invalid SQL on some dialects and a pointless query on all of them.
        if (courseId == null || studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        return enrollmentRepo.enrolledStudentIds(courseId, studentIds);
    }

    @Override
    @Transactional
    public void saveEnrollment(UUID studentId, UUID courseId) {
        CourseEnrollmentEntity e = new CourseEnrollmentEntity();
        e.setStudent(studentRepo.getReferenceById(studentId));
        e.setCourse(courseRepo.getReferenceById(courseId));
        e.setEnrollmentDate(LocalDate.now());
        e.setStatus(STATUS_EFFECTIVE);
        enrollmentRepo.save(e);
    }

    @Override
    public PageResult<CourseStudent> studentsByCourse(UUID courseId, PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        return SpringPaging.toPageResult(
            enrollmentRepo.findByCourse_Id(courseId, pageable).map(this::toCourseStudent));
    }

    @Override
    public List<UUID> courseIdsOfStudent(UUID studentId) {
        return enrollmentRepo.findCourseIdsOfStudent(studentId);
    }

    @Override
    public Map<UUID, UUID> courseIdsByEnrollment(Collection<UUID> courseEnrollmentIds) {
        if (courseEnrollmentIds == null || courseEnrollmentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> byEnrollment = new HashMap<>();
        for (Object[] row : enrollmentRepo.findCourseIdsByEnrollment(courseEnrollmentIds)) {
            byEnrollment.put((UUID) row[0], (UUID) row[1]);
        }
        return byEnrollment;
    }

    @Override
    public PageResult<CourseStudent> activeStudentsByCourse(UUID courseId, PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        return SpringPaging.toPageResult(
            enrollmentRepo.findByCourse_IdAndStatus(courseId, STATUS_EFFECTIVE, pageable)
                .map(this::toCourseStudent));
    }

    @Override
    public Optional<CourseStudent> courseStudentById(UUID courseEnrollmentId) {
        return enrollmentRepo.findById(courseEnrollmentId).map(this::toCourseStudent);
    }

    @Override
    @Transactional
    public int withdrawActiveEnrollments(UUID studentId) {
        List<CourseEnrollmentEntity> active =
            enrollmentRepo.findByStudent_IdAndStatus(studentId, STATUS_EFFECTIVE);
        for (CourseEnrollmentEntity e : active) {
            e.setStatus(STATUS_WITHDRAWN);
        }
        enrollmentRepo.saveAll(active);
        return active.size();
    }

    @Override
    public UUID courseOfEnrollment(UUID courseEnrollmentId) {
        return enrollmentRepo.findById(courseEnrollmentId)
            .map(e -> e.getCourse().getId())
            .orElseThrow(() -> new ResourceNotFoundException("CourseEnrollment", courseEnrollmentId));
    }

    private CourseStudent toCourseStudent(CourseEnrollmentEntity e) {
        StudentEntity s = e.getStudent();
        return new CourseStudent(
            e.getId(), s.getId(), s.getRudeCode(), s.getIdentityCard(),
            s.getNames(), s.getLastNames(), e.getStatus(), s.getGender());
    }
}
