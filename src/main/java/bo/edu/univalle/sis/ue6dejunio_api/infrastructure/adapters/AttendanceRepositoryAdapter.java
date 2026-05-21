package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.RegisterAttendanceCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AttendanceEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAttendanceRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaEnrollmentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class AttendanceRepositoryAdapter implements IAttendanceDomain {

    private final JpaAttendanceRepository attendanceRepo;
    private final JpaEnrollmentRepository enrollmentRepo;

    public AttendanceRepositoryAdapter(JpaAttendanceRepository attendanceRepo,
                                       JpaEnrollmentRepository enrollmentRepo) {
        this.attendanceRepo = attendanceRepo;
        this.enrollmentRepo = enrollmentRepo;
    }

    @Override
    public boolean enrollmentExists(UUID enrollmentId) {
        return enrollmentRepo.existsById(enrollmentId);
    }

    @Override
    @Transactional
    public Attendance upsert(RegisterAttendanceCommand c) {
        AttendanceEntity e = attendanceRepo
            .findByEnrollment_IdAndDate(c.enrollmentId(), c.date())
            .orElseGet(() -> {
                AttendanceEntity n = new AttendanceEntity();
                n.setEnrollment(enrollmentRepo.getReferenceById(c.enrollmentId()));
                n.setDate(c.date());
                return n;
            });
        e.setStatus(c.status());
        return toDomain(attendanceRepo.save(e));
    }

    @Override
    public List<Attendance> findByEnrollment(UUID enrollmentId) {
        return attendanceRepo.findByEnrollment_IdOrderByDate(enrollmentId).stream()
            .map(this::toDomain).toList();
    }

    private Attendance toDomain(AttendanceEntity e) {
        return new Attendance(e.getId(), e.getEnrollment().getId(), e.getDate(), e.getStatus());
    }
}
