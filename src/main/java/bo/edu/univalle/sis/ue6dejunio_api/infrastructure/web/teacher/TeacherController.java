package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.teacher;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.TeacherStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.TeacherStudentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @Operation(summary = "Lista paginada de estudiantes del docente con sus enrollments por materia")
    public ResponseEntity<PagedResponse<TeacherStudentResponse>> students(
        @PathVariable UUID userId,
        @ParameterObject Pageable pageable
    ) {
        Page<TeacherStudent> page = enrollmentService.studentsOfTeacher(userId, pageable);
        return ResponseEntity.ok(PagedResponse.of(page.map(TeacherStudentResponse::from)));
    }
}
