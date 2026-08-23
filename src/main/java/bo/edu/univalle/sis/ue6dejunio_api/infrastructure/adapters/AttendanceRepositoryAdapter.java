package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyStatusCount;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AttendanceEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAttendanceRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseEnrollmentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class AttendanceRepositoryAdapter implements IAttendanceDomain {

    private final JpaAttendanceRepository attendanceRepo;
    private final JpaCourseEnrollmentRepository enrollmentRepo;
    private final JpaClassGroupRepository classGroupRepo;
    private final JpaCourseRepository courseRepo;

    public AttendanceRepositoryAdapter(JpaAttendanceRepository attendanceRepo,
                                       JpaCourseEnrollmentRepository enrollmentRepo,
                                       JpaClassGroupRepository classGroupRepo,
                                       JpaCourseRepository courseRepo) {
        this.attendanceRepo = attendanceRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.classGroupRepo = classGroupRepo;
        this.courseRepo = courseRepo;
    }

    @Override
    public boolean courseEnrollmentExists(UUID courseEnrollmentId) {
        return enrollmentRepo.existsById(courseEnrollmentId);
    }

    @Override
    public Set<UUID> existingCourseEnrollmentIds(Collection<UUID> courseEnrollmentIds) {
        if (courseEnrollmentIds.isEmpty()) {
            return Set.of();
        }
        // Id projection, not findAllById: the caller only needs to know which ids exist, and
        // CourseEnrollmentEntity.student is EAGER, so hydrating the entities would fire a
        // secondary select per row.
        return new HashSet<>(enrollmentRepo.findExistingIds(courseEnrollmentIds));
    }

    @Override
    public Set<UUID> courseEnrollmentIdsInCourse(Collection<UUID> courseEnrollmentIds, UUID courseId) {
        if (courseEnrollmentIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(enrollmentRepo.findIdsInCourse(courseEnrollmentIds, courseId));
    }

    @Override
    public boolean courseExists(UUID courseId) {
        return courseRepo.existsById(courseId);
    }

    @Override
    public List<DailyStatusCount> dailyStatusCountsByCourseGroupedByDate(UUID courseId) {
        return attendanceRepo.dailyStatusCountsByCourseGroupedByDate(courseId).stream()
            .map(row -> new DailyStatusCount(
                (LocalDate) row[0], (String) row[1], ((Number) row[2]).longValue()))
            .toList();
    }

    @Override
    public Integer academicYearOfCourse(UUID courseId) {
        return courseRepo.findById(courseId)
            .map(c -> c.getAcademicYear().getId())
            .orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
    }

    @Override
    public UUID courseOfCourseEnrollment(UUID courseEnrollmentId) {
        return enrollmentRepo.findById(courseEnrollmentId)
            .map(e -> e.getCourse().getId())
            .orElseThrow(() -> new ResourceNotFoundException("CourseEnrollment", courseEnrollmentId));
    }

    @Override
    public UUID courseOfClassGroup(UUID classGroupId) {
        return classGroupRepo.findById(classGroupId)
            .map(cg -> cg.getCourse().getId())
            .orElseThrow(() -> new ResourceNotFoundException("ClassGroup", classGroupId));
    }

    @Override
    public UUID teacherOfClassGroup(UUID classGroupId) {
        return classGroupRepo.findById(classGroupId)
            .map(cg -> cg.getTeacher() != null ? cg.getTeacher().getId() : null)
            .orElseThrow(() -> new ResourceNotFoundException("ClassGroup", classGroupId));
    }

    @Override
    @Transactional
    public Attendance upsertDaily(UUID courseEnrollmentId, LocalDate date, String status) {
        AttendanceEntity e = attendanceRepo
            .findByCourseEnrollment_IdAndDateAndClassGroupIsNull(courseEnrollmentId, date)
            .orElseGet(() -> {
                AttendanceEntity n = new AttendanceEntity();
                n.setCourseEnrollment(enrollmentRepo.getReferenceById(courseEnrollmentId));
                n.setClassGroup(null);
                n.setDate(date);
                return n;
            });
        e.setStatus(status);
        return toDomain(attendanceRepo.save(e));
    }

    @Override
    @Transactional
    public Attendance upsertSession(UUID courseEnrollmentId, UUID classGroupId, LocalDate date, String status) {
        AttendanceEntity e = attendanceRepo
            .findByCourseEnrollment_IdAndClassGroup_IdAndDate(courseEnrollmentId, classGroupId, date)
            .orElseGet(() -> {
                AttendanceEntity n = new AttendanceEntity();
                n.setCourseEnrollment(enrollmentRepo.getReferenceById(courseEnrollmentId));
                n.setClassGroup(classGroupRepo.getReferenceById(classGroupId));
                n.setDate(date);
                return n;
            });
        e.setStatus(status);
        return toDomain(attendanceRepo.save(e));
    }

    @Override
    @Transactional
    public List<Attendance> upsertDailyBatch(LocalDate date, Map<UUID, String> statusByCourseEnrollmentId) {
        if (statusByCourseEnrollmentId.isEmpty()) {
            return List.of();
        }
        List<AttendanceEntity> existing = attendanceRepo
            .findByCourseEnrollment_IdInAndDateAndClassGroupIsNull(
                statusByCourseEnrollmentId.keySet(), date);
        Map<UUID, AttendanceEntity> existingByEnrollmentId = existing.stream()
            .collect(Collectors.toMap(e -> e.getCourseEnrollment().getId(), e -> e));

        List<AttendanceEntity> toSave = new ArrayList<>(statusByCourseEnrollmentId.size());
        for (Map.Entry<UUID, String> mark : statusByCourseEnrollmentId.entrySet()) {
            UUID courseEnrollmentId = mark.getKey();
            AttendanceEntity e = existingByEnrollmentId.get(courseEnrollmentId);
            if (e == null) {
                e = new AttendanceEntity();
                e.setCourseEnrollment(enrollmentRepo.getReferenceById(courseEnrollmentId));
                e.setClassGroup(null);
                e.setDate(date);
            }
            e.setStatus(mark.getValue());
            toSave.add(e);
        }
        return attendanceRepo.saveAll(toSave).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public List<Attendance> upsertSessionBatch(UUID classGroupId, LocalDate date,
                                               Map<UUID, String> statusByCourseEnrollmentId) {
        if (statusByCourseEnrollmentId.isEmpty()) {
            return List.of();
        }
        List<AttendanceEntity> existing = attendanceRepo
            .findByCourseEnrollment_IdInAndDateAndClassGroup_Id(
                statusByCourseEnrollmentId.keySet(), date, classGroupId);
        Map<UUID, AttendanceEntity> existingByEnrollmentId = existing.stream()
            .collect(Collectors.toMap(e -> e.getCourseEnrollment().getId(), e -> e));

        List<AttendanceEntity> toSave = new ArrayList<>(statusByCourseEnrollmentId.size());
        for (Map.Entry<UUID, String> mark : statusByCourseEnrollmentId.entrySet()) {
            UUID courseEnrollmentId = mark.getKey();
            AttendanceEntity e = existingByEnrollmentId.get(courseEnrollmentId);
            if (e == null) {
                e = new AttendanceEntity();
                e.setCourseEnrollment(enrollmentRepo.getReferenceById(courseEnrollmentId));
                e.setClassGroup(classGroupRepo.getReferenceById(classGroupId));
                e.setDate(date);
            }
            e.setStatus(mark.getValue());
            toSave.add(e);
        }
        return attendanceRepo.saveAll(toSave).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Attendance> byCourseEnrollment(UUID courseEnrollmentId) {
        return attendanceRepo.findByCourseEnrollment_IdOrderByDate(courseEnrollmentId)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Attendance> dailyByCourseEnrollment(UUID courseEnrollmentId) {
        return attendanceRepo.findByCourseEnrollment_IdAndClassGroupIsNullOrderByDate(courseEnrollmentId)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Attendance> dailyByCourseEnrollmentIn(Collection<UUID> courseEnrollmentIds) {
        if (courseEnrollmentIds.isEmpty()) {
            return List.of();
        }
        return attendanceRepo.findByCourseEnrollment_IdInAndClassGroupIsNullOrderByDate(courseEnrollmentIds)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Attendance> sessionByClassGroupAndCourseEnrollmentIn(
        UUID classGroupId, Collection<UUID> courseEnrollmentIds) {
        if (courseEnrollmentIds.isEmpty()) {
            return List.of();
        }
        return attendanceRepo
            .findByCourseEnrollment_IdInAndClassGroup_IdOrderByDate(courseEnrollmentIds, classGroupId)
            .stream().map(this::toDomain).toList();
    }

    private Attendance toDomain(AttendanceEntity e) {
        return new Attendance(e.getId(), e.getCourseEnrollment().getId(),
            e.getClassGroup() != null ? e.getClassGroup().getId() : null,
            e.getDate(), e.getStatus(),
            e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedBy(), e.getUpdatedAt());
    }
}
