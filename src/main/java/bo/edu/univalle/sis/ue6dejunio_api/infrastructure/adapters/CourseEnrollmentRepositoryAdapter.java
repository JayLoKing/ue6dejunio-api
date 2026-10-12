package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.CourseStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollmentOutcome;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollmentSeat;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.FrozenResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.YearSeat;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook.AnnualResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEnrollmentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseEnrollmentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaStudentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CourseEnrollmentRepositoryAdapter implements ICourseEnrollmentDomain {

    private static final String STATUS_EFFECTIVE = "Effective";
    private static final String STATUS_WITHDRAWN = "Withdrawn";

    private final JpaCourseEnrollmentRepository enrollmentRepo;
    private final JpaStudentRepository studentRepo;
    private final JpaCourseRepository courseRepo;
    private final JdbcTemplate jdbc;

    public CourseEnrollmentRepositoryAdapter(
            JpaCourseEnrollmentRepository enrollmentRepo,
            JpaStudentRepository studentRepo,
            JpaCourseRepository courseRepo,
            JdbcTemplate jdbc) {
        this.enrollmentRepo = enrollmentRepo;
        this.studentRepo = studentRepo;
        this.courseRepo = courseRepo;
        this.jdbc = jdbc;
    }

    @Override
    public boolean courseExists(UUID courseId) {
        return courseRepo.existsById(courseId);
    }

    @Override
    public Map<UUID, String> enrollmentStatusByStudent(UUID courseId, Collection<UUID> studentIds) {
        // An empty IN is invalid SQL on some dialects and a pointless query on all of them.
        if (courseId == null || studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> byStudent = new HashMap<>();
        for (Object[] row : enrollmentRepo.enrollmentStatuses(courseId, studentIds)) {
            byStudent.put((UUID) row[0], (String) row[1]);
        }
        return byStudent;
    }

    @Override
    public List<EnrollmentSeat> seatsOfStudentInYear(UUID studentId, Integer academicYearId) {
        return enrollmentRepo.seatsOfStudentInYear(studentId, academicYearId).stream()
                .map(row -> new EnrollmentSeat((UUID) row[0], (String) row[1]))
                .toList();
    }

    @Override
    public List<YearSeat> seatsInYearOfCourse(UUID courseId, Collection<UUID> studentIds) {
        // An empty IN is invalid SQL on some dialects and a pointless query on all of them.
        if (courseId == null || studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        return enrollmentRepo.seatsInYearOfCourse(courseId, studentIds).stream()
                .map(
                        row ->
                                new YearSeat(
                                        (UUID) row[0],
                                        (UUID) row[1],
                                        row[2] + " " + row[3],
                                        (String) row[4]))
                .toList();
    }

    @Override
    public Set<UUID> studentsEffectiveInYear(Integer academicYearId, Collection<UUID> studentIds) {
        if (academicYearId == null || studentIds == null || studentIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                enrollmentRepo.studentIdsWithStatusInYear(
                        academicYearId, STATUS_EFFECTIVE, studentIds));
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
    @Transactional
    public void reactivateEnrollments(UUID courseId, Collection<UUID> studentIds) {
        if (courseId == null || studentIds == null || studentIds.isEmpty()) {
            return;
        }
        // The date is restated because this is the day they came back, not the day they first
        // enrolled — the row is the same one only because the unique index leaves no other.
        enrollmentRepo.reactivateEnrollments(
                courseId, studentIds, STATUS_EFFECTIVE, LocalDate.now());
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
    public Map<UUID, Long> enrolmentCountsByCourse(Integer academicYearId) {
        Map<UUID, Long> byCourse = new HashMap<>();
        for (Object[] row : enrollmentRepo.countByCourseOfAcademicYear(academicYearId)) {
            byCourse.put((UUID) row[0], (Long) row[1]);
        }
        return byCourse;
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
                enrollmentRepo
                        .findByCourse_IdAndStatus(courseId, STATUS_EFFECTIVE, pageable)
                        .map(this::toCourseStudent));
    }

    @Override
    public Optional<CourseStudent> courseStudentById(UUID courseEnrollmentId) {
        return enrollmentRepo.findById(courseEnrollmentId).map(this::toCourseStudent);
    }

    @Override
    @Transactional
    public int withdrawActiveEnrollments(UUID studentId) {
        return enrollmentRepo.withdrawEffectiveInOpenYears(
                studentId, STATUS_EFFECTIVE, STATUS_WITHDRAWN);
    }

    @Override
    public Map<String, Long> genderCountsOfCourse(UUID courseId) {
        Map<String, Long> byGender = new HashMap<>();
        for (Object[] row :
                enrollmentRepo.countByCourseAndStatusGroupedByGender(courseId, STATUS_EFFECTIVE)) {
            if (row[0] != null) {
                byGender.put((String) row[0], (Long) row[1]);
            }
        }
        return byGender;
    }

    @Override
    public long activeEnrolmentCountOfCourse(UUID courseId) {
        return enrollmentRepo.countByCourse_IdAndStatus(courseId, STATUS_EFFECTIVE);
    }

    @Override
    public UUID courseOfEnrollment(UUID courseEnrollmentId) {
        return enrollmentRepo
                .findById(courseEnrollmentId)
                .map(e -> e.getCourse().getId())
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "CourseEnrollment", courseEnrollmentId));
    }

    @Override
    @Transactional
    public void closeEnrollmentsOfYear(int academicYearId, LocalDateTime closedAt) {
        enrollmentRepo.closeOfYear(academicYearId, closedAt, STATUS_WITHDRAWN);
    }

    @Override
    @Transactional
    public void freezeResults(Collection<FrozenResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        // One JDBC batch for the whole set: the values differ per enrollment, so no single JPQL
        // update can carry them, and a save per entity would be a statement per student.
        List<FrozenResult> rows = List.copyOf(results);
        jdbc.batchUpdate(
                "UPDATE course_enrollments SET final_average = ?, final_result = ? "
                        + "WHERE id_course_enrollment = ? AND status = ?",
                rows,
                rows.size(),
                (ps, r) -> {
                    ps.setBigDecimal(1, r.finalAverage());
                    ps.setString(2, r.result() == null ? null : r.result().name());
                    ps.setObject(3, r.courseEnrollmentId());
                    ps.setString(4, STATUS_EFFECTIVE);
                });
    }

    @Override
    @Transactional
    public void reopenEnrollmentsOfYear(int academicYearId) {
        enrollmentRepo.reopenOfYear(academicYearId);
    }

    @Override
    public Map<UUID, EnrollmentOutcome> outcomesOf(Collection<UUID> courseEnrollmentIds) {
        // An empty IN is invalid SQL on some dialects and a pointless query on all of them.
        if (courseEnrollmentIds == null || courseEnrollmentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, EnrollmentOutcome> byEnrollment = new HashMap<>();
        for (Object[] row : enrollmentRepo.outcomesOf(courseEnrollmentIds)) {
            UUID id = (UUID) row[0];
            String result = (String) row[2];
            byEnrollment.put(
                    id,
                    new EnrollmentOutcome(
                            id,
                            row[3] != null,
                            (BigDecimal) row[1],
                            result == null ? null : AnnualResult.valueOf(result)));
        }
        return byEnrollment;
    }

    private CourseStudent toCourseStudent(CourseEnrollmentEntity e) {
        StudentEntity s = e.getStudent();
        return new CourseStudent(
                e.getId(),
                s.getId(),
                s.getRudeCode(),
                s.getIdentityCard(),
                s.getNames(),
                s.getLastNames(),
                e.getStatus(),
                s.getGender());
    }
}
