package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.attendance;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.AttendanceResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.BatchResultResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.DailyAttendanceRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.DailyBatchRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.SessionAttendanceRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.SessionBatchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
@Tag(
        name = "Attendance",
        description = "Asistencia dual: diaria de curso (aula) / sesion de materia (tecnico)")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceController {

    private final IAttendanceService attendanceService;

    public AttendanceController(IAttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/daily")
    @PreAuthorize("@authz.canWriteDailyAttendance(authentication, #r.courseEnrollmentId())")
    @Operation(summary = "Asistencia diaria de curso (id_class_group NULL). Regularidad oficial")
    public ResponseEntity<AttendanceResponse> daily(@Valid @RequestBody DailyAttendanceRequest r) {
        return ResponseEntity.ok(
                AttendanceResponse.from(
                        attendanceService.registerDaily(
                                r.courseEnrollmentId(), r.date(), r.status())));
    }

    @PostMapping("/daily/batch")
    @PreAuthorize("@authz.canWriteDailyBatch(authentication, #r.records().![courseEnrollmentId()])")
    @Operation(summary = "Asistencia diaria de todo el curso en una fecha")
    public ResponseEntity<BatchResultResponse> dailyBatch(@Valid @RequestBody DailyBatchRequest r) {
        List<IAttendanceService.DailyMark> marks =
                r.records().stream()
                        .map(
                                m ->
                                        new IAttendanceService.DailyMark(
                                                m.courseEnrollmentId(), m.status()))
                        .toList();
        return ResponseEntity.ok(
                BatchResultResponse.from(attendanceService.registerDailyBatch(r.date(), marks)));
    }

    @PostMapping("/session")
    @PreAuthorize("@authz.canWriteClassGroup(authentication, #r.classGroupId())")
    @Operation(summary = "Asistencia de sesion de materia (docente tecnico)")
    public ResponseEntity<AttendanceResponse> session(
            @Valid @RequestBody SessionAttendanceRequest r) {
        return ResponseEntity.ok(
                AttendanceResponse.from(
                        attendanceService.registerSession(
                                r.courseEnrollmentId(), r.classGroupId(), r.date(), r.status())));
    }

    @PostMapping("/session/batch")
    @PreAuthorize("@authz.canWriteClassGroup(authentication, #r.classGroupId())")
    @Operation(summary = "Asistencia de sesion de toda la materia en una fecha")
    public ResponseEntity<BatchResultResponse> sessionBatch(
            @Valid @RequestBody SessionBatchRequest r) {
        List<IAttendanceService.DailyMark> marks =
                r.records().stream()
                        .map(
                                m ->
                                        new IAttendanceService.DailyMark(
                                                m.courseEnrollmentId(), m.status()))
                        .toList();
        return ResponseEntity.ok(
                BatchResultResponse.from(
                        attendanceService.registerSessionBatch(r.classGroupId(), r.date(), marks)));
    }

    @GetMapping
    @PreAuthorize("@authz.canReadEnrollmentScope(authentication, #courseEnrollmentId)")
    @Operation(summary = "Asistencia de un course_enrollment (diaria + sesiones)")
    public ResponseEntity<List<AttendanceResponse>> byCourseEnrollment(
            @RequestParam("id_course_enrollment") UUID courseEnrollmentId) {
        return ResponseEntity.ok(
                attendanceService.byCourseEnrollment(courseEnrollmentId).stream()
                        .map(AttendanceResponse::from)
                        .toList());
    }
}
