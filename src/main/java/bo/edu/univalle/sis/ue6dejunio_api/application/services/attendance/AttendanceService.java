package bo.edu.univalle.sis.ue6dejunio_api.application.services.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.AttendanceBatchResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyAttendanceCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.DailyAttendanceResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.RegisterAttendanceCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AttendanceService implements IAttendanceService {

    private final IAttendanceDomain attendanceDomain;
    private final IClassGroupDomain classGroupDomain;
    private final IEnrollmentDomain enrollmentDomain;

    public AttendanceService(IAttendanceDomain attendanceDomain,
                             IClassGroupDomain classGroupDomain,
                             IEnrollmentDomain enrollmentDomain) {
        this.attendanceDomain = attendanceDomain;
        this.classGroupDomain = classGroupDomain;
        this.enrollmentDomain = enrollmentDomain;
    }

    @Override
    @Transactional
    public Attendance register(RegisterAttendanceCommand command) {
        if (!attendanceDomain.enrollmentExists(command.enrollmentId())) {
            throw new ResourceNotFoundException("Enrollment", command.enrollmentId());
        }
        return attendanceDomain.upsert(command);
    }

    @Override
    @Transactional
    public AttendanceBatchResult registerBatch(List<RegisterAttendanceCommand> commands) {
        int saved = 0;
        for (RegisterAttendanceCommand c : commands) {
            if (!attendanceDomain.enrollmentExists(c.enrollmentId())) {
                throw new ResourceNotFoundException("Enrollment", c.enrollmentId());
            }
            attendanceDomain.upsert(c);
            saved++;
        }
        return new AttendanceBatchResult(commands.size(), saved);
    }

    @Override
    @Transactional
    public DailyAttendanceResult registerDaily(DailyAttendanceCommand command) {
        Integer yearId = classGroupDomain.currentAcademicYearId();
        List<UUID> classGroupIds = classGroupDomain.classGroupIdsByCourse(
            command.gradeId(), command.parallelId(), yearId);
        if (classGroupIds.isEmpty()) {
            throw new ResourceNotFoundException("Curso sin materias asignadas (class_groups)",
                "grado=" + command.gradeId() + " paralelo=" + command.parallelId());
        }

        int saved = 0;
        List<UUID> notEnrolled = new ArrayList<>();
        for (DailyAttendanceCommand.StudentMark mark : command.records()) {
            boolean any = false;
            for (UUID cgId : classGroupIds) {
                Optional<UUID> enrollmentId = enrollmentDomain.findEnrollmentId(mark.studentId(), cgId);
                if (enrollmentId.isEmpty()) {
                    continue;
                }
                attendanceDomain.upsert(new RegisterAttendanceCommand(
                    enrollmentId.get(), command.date(), mark.status()));
                saved++;
                any = true;
            }
            if (!any) {
                notEnrolled.add(mark.studentId());
            }
        }
        return new DailyAttendanceResult(
            command.records().size(), classGroupIds.size(), saved, notEnrolled);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Attendance> byEnrollment(UUID enrollmentId) {
        return attendanceDomain.findByEnrollment(enrollmentId);
    }
}
