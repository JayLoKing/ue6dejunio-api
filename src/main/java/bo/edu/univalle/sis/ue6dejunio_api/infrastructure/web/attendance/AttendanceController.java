package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.Attendance;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.AttendanceBatchResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.attendance.RegisterAttendanceCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.AttendanceBatchRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.AttendanceResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.RegisterAttendanceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/attendance")
@Tag(name = "Attendance", description = "Cuaderno pedagogico: asistencia")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceController {

    private final IAttendanceService attendanceService;

    public AttendanceController(IAttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping
    @Operation(summary = "Registrar/actualizar asistencia de una inscripcion en una fecha")
    public ResponseEntity<AttendanceResponse> register(@Valid @RequestBody RegisterAttendanceRequest request) {
        Attendance saved = attendanceService.register(new RegisterAttendanceCommand(
            request.enrollmentId(), request.date(), request.status()));
        return ResponseEntity.ok(AttendanceResponse.from(saved));
    }

    @PostMapping("/batch")
    @Operation(summary = "Registrar asistencia de todo el curso en una fecha")
    public ResponseEntity<AttendanceBatchResult> registerBatch(@Valid @RequestBody AttendanceBatchRequest request) {
        List<RegisterAttendanceCommand> commands = request.records().stream()
            .map(r -> new RegisterAttendanceCommand(r.enrollmentId(), request.date(), r.status()))
            .toList();
        return ResponseEntity.ok(attendanceService.registerBatch(commands));
    }

    @GetMapping("/enrollment/{enrollmentId}")
    @Operation(summary = "Listar asistencia de una inscripcion")
    public ResponseEntity<List<AttendanceResponse>> byEnrollment(@PathVariable UUID enrollmentId) {
        List<AttendanceResponse> body = attendanceService.byEnrollment(enrollmentId).stream()
            .map(AttendanceResponse::from).toList();
        return ResponseEntity.ok(body);
    }
}
