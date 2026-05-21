package bo.edu.univalle.sis.ue6dejunio_api.application.services.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.AttendanceBatchResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.RegisterAttendanceCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AttendanceService implements IAttendanceService {

    private final IAttendanceDomain attendanceDomain;

    public AttendanceService(IAttendanceDomain attendanceDomain) {
        this.attendanceDomain = attendanceDomain;
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
    @Transactional(readOnly = true)
    public List<Attendance> byEnrollment(UUID enrollmentId) {
        return attendanceDomain.findByEnrollment(enrollmentId);
    }
}
