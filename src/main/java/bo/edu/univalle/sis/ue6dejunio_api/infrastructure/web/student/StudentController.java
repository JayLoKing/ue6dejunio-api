package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/students")
@Tag(name = "Students", description = "Consulta de estudiantes")
@SecurityRequirement(name = "bearerAuth")
public class StudentController {

    private final IStudentService studentService;

    public StudentController(IStudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener estudiante por id")
    public ResponseEntity<StudentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(StudentResponse.from(studentService.getById(id)));
    }
}
