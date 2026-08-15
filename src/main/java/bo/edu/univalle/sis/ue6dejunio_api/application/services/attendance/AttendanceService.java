package bo.edu.univalle.sis.ue6dejunio_api.application.services.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyBatchResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class AttendanceService implements IAttendanceService {

    private final IAttendanceDomain attendanceDomain;
    private final Clock clock;

    public AttendanceService(IAttendanceDomain attendanceDomain, Clock clock) {
        this.attendanceDomain = attendanceDomain;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Attendance registerDaily(UUID courseEnrollmentId, LocalDate date, String status) {
        requireCurrentDay(date);
        if (!attendanceDomain.courseEnrollmentExists(courseEnrollmentId)) {
            throw new ResourceNotFoundException("CourseEnrollment", courseEnrollmentId);
        }
        return attendanceDomain.upsertDaily(courseEnrollmentId, date, status);
    }

    @Override
    @Transactional
    public DailyBatchResult registerDailyBatch(LocalDate date, List<DailyMark> marks) {
        requireCurrentDay(date);
        int saved = 0;
        for (DailyMark m : marks) {
            if (!attendanceDomain.courseEnrollmentExists(m.courseEnrollmentId())) {
                throw new ResourceNotFoundException("CourseEnrollment", m.courseEnrollmentId());
            }
            attendanceDomain.upsertDaily(m.courseEnrollmentId(), date, m.status());
            saved++;
        }
        return new DailyBatchResult(marks.size(), saved);
    }

    private void requireCurrentDay(LocalDate date) {
        if (!LocalDate.now(clock).equals(date)) {
            throw new ConflictException("no puede modificar registros de dias anteriores");
        }
    }

    @Override
    @Transactional
    public Attendance registerSession(UUID courseEnrollmentId, UUID classGroupId, LocalDate date, String status) {
        if (!attendanceDomain.courseEnrollmentExists(courseEnrollmentId)) {
            throw new ResourceNotFoundException("CourseEnrollment", courseEnrollmentId);
        }
        UUID ceCourse = attendanceDomain.courseOfCourseEnrollment(courseEnrollmentId);
        UUID cgCourse = attendanceDomain.courseOfClassGroup(classGroupId);
        if (!ceCourse.equals(cgCourse)) {
            throw new IllegalArgumentException("El estudiante no pertenece al curso de la materia");
        }
        return attendanceDomain.upsertSession(courseEnrollmentId, classGroupId, date, status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Attendance> byCourseEnrollment(UUID courseEnrollmentId) {
        return attendanceDomain.byCourseEnrollment(courseEnrollmentId);
    }
}
