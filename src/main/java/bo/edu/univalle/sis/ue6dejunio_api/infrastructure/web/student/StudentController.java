package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.BatchResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.BatchStudentRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.BatchStudentResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateStudentRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentResponse;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/students")
@Tag(name = "Students", description = "Gestion de estudiantes")
@SecurityRequirement(name = "bearerAuth")
public class StudentController {

    private final IStudentService studentService;

    public StudentController(IStudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping
    @Operation(summary = "Registrar un estudiante (formulario manual)")
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody CreateStudentRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        Student created = studentService.create(toCommand(request));
        URI location = uriBuilder.path("/api/students/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(StudentResponse.from(created));
    }

    @PostMapping("/batch")
    @Operation(summary = "Registrar estudiantes en lote (nomina PDF). Procesa por fila, reporta fallos")
    public ResponseEntity<BatchStudentResponse> createBatch(@Valid @RequestBody BatchStudentRequest request) {
        List<CreateStudentCommand> commands = request.students().stream()
            .map(StudentController::toCommand)
            .toList();
        BatchResult result = studentService.createBatch(commands);
        return ResponseEntity.ok(BatchStudentResponse.from(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener estudiante por id")
    public ResponseEntity<StudentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(StudentResponse.from(studentService.getById(id)));
    }

    private static CreateStudentCommand toCommand(CreateStudentRequest r) {
        return new CreateStudentCommand(
            r.rudeCode(), r.identityCard(), r.names(), r.lastNames(), r.birthDate(), r.gender()
        );
    }
}
