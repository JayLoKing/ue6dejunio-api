package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.teacher;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teachers")
@Tag(name = "Teachers", description = "Vistas centradas en el docente")
@SecurityRequirement(name = "bearerAuth")
public class TeacherController {

    private final IEnrollmentService enrollmentService;

    public TeacherController(IEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/{userId}/students")
    @Operation(summary = "Lista distinta de estudiantes del docente (todos sus class_groups del anio actual)")
    public ResponseEntity<List<StudentResponse>> students(@PathVariable UUID userId) {
        List<StudentResponse> body = enrollmentService.studentsOfTeacher(userId).stream()
            .map(StudentResponse::from).toList();
        return ResponseEntity.ok(body);
    }
}
