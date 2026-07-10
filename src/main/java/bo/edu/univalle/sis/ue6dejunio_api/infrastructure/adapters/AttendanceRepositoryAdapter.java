package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AttendanceEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAttendanceRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseEnrollmentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class AttendanceRepositoryAdapter implements IAttendanceDomain {

    private final JpaAttendanceRepository attendanceRepo;
    private final JpaCourseEnrollmentRepository enrollmentRepo;
    private final JpaClassGroupRepository classGroupRepo;

    public AttendanceRepositoryAdapter(JpaAttendanceRepository attendanceRepo,
                                       JpaCourseEnrollmentRepository enrollmentRepo,
                                       JpaClassGroupRepository classGroupRepo) {
        this.attendanceRepo = attendanceRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.classGroupRepo = classGroupRepo;
    }

    @Override
    public boolean courseEnrollmentExists(UUID courseEnrollmentId) {
        return enrollmentRepo.existsById(courseEnrollmentId);
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
    public List<Attendance> byCourseEnrollment(UUID courseEnrollmentId) {
        return attendanceRepo.findByCourseEnrollment_IdOrderByDate(courseEnrollmentId)
            .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Attendance> dailyByCourseEnrollment(UUID courseEnrollmentId) {
        return attendanceRepo.findByCourseEnrollment_IdAndClassGroupIsNullOrderByDate(courseEnrollmentId)
            .stream().map(this::toDomain).toList();
    }

    private Attendance toDomain(AttendanceEntity e) {
        return new Attendance(e.getId(), e.getCourseEnrollment().getId(),
            e.getClassGroup() != null ? e.getClassGroup().getId() : null,
            e.getDate(), e.getStatus());
    }
}
