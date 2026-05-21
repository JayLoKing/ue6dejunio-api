package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.enrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateStudentRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.EnrollCourseRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.EnrollResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.EnrollStudentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/enrollments")
@Tag(name = "Enrollments", description = "Inscripcion de estudiantes a un curso (registra + inscribe cruzado)")
@SecurityRequirement(name = "bearerAuth")
public class EnrollmentController {

    private final IEnrollmentService enrollmentService;

    public EnrollmentController(IEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    @Operation(summary = "Inscripcion manual de un estudiante (registra + inscribe en todas las materias del curso)")
    public ResponseEntity<EnrollResponse> enrollSingle(@Valid @RequestBody EnrollStudentRequest request) {
        EnrollResult result = enrollmentService.enrollCourse(new EnrollCourseCommand(
            request.gradeId(), request.parallelId(), List.of(toCommand(request.student()))));
        return ResponseEntity.ok(EnrollResponse.from(result));
    }

    @PostMapping("/batch")
    @Operation(summary = "Inscripcion masiva (nomina PDF). Registra estudiantes e inscribe cruzado. Transaccion ACID")
    public ResponseEntity<EnrollResponse> enrollBatch(@Valid @RequestBody EnrollCourseRequest request) {
        List<CreateStudentCommand> students = request.students().stream()
            .map(EnrollmentController::toCommand)
            .toList();
        EnrollResult result = enrollmentService.enrollCourse(
            new EnrollCourseCommand(request.gradeId(), request.parallelId(), students));
        return ResponseEntity.ok(EnrollResponse.from(result));
    }

    private static CreateStudentCommand toCommand(CreateStudentRequest s) {
        return new CreateStudentCommand(
            s.rudeCode(), s.identityCard(), s.names(), s.lastNames(), s.birthDate(), s.gender());
    }
}
