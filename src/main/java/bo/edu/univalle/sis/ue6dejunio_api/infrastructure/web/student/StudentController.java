package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawalReason;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security.AuthorizationComponent;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentDirectoryResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.WithdrawStudentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/students")
@Tag(name = "Students", description = "Consulta de estudiantes")
@SecurityRequirement(name = "bearerAuth")
public class StudentController {

    private final IStudentService studentService;
    private final AuthorizationComponent authz;

    public StudentController(IStudentService studentService, AuthorizationComponent authz) {
        this.studentService = studentService;
        this.authz = authz;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener estudiante por id")
    public ResponseEntity<StudentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(StudentResponse.from(studentService.getById(id)));
    }

    @GetMapping("/search")
    @Operation(summary = "Buscar estudiantes por nombre o RUDE. Docente acotado a su curso de aula")
    public ResponseEntity<PagedResponse<StudentDirectoryResponse>> search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) UUID courseId,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit,
        Authentication authentication
    ) {
        UUID effectiveCourseId = authz.effectiveDirectoryCourseId(authentication, courseId);
        Pageable pageable = PageRequest.of(offset - 1, limit, Sort.by("id"));
        return ResponseEntity.ok(PagedResponse.of(
            studentService.search(q, effectiveCourseId, pageable).map(StudentDirectoryResponse::from)));
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Baja logica de estudiante (retiro/transferencia/otro)")
    public ResponseEntity<Void> withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawStudentRequest request) {
        StudentWithdrawalReason reason = StudentWithdrawalReason.fromRequestValue(request.reason())
            .orElseThrow(() -> new ValidationException("Motivo de baja invalido: " + request.reason()));
        studentService.withdraw(id, reason);
        return ResponseEntity.noContent().build();
    }
}
