package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.teacher;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.enrollment.TeacherStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.enrollment.IEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.TeacherStudentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/teachers")
@Tag(name = "Teachers", description = "Vistas centradas en el docente")
@SecurityRequirement(name = "bearerAuth")
public class TeacherController {

    private final IEnrollmentService enrollmentService;

    public TeacherController(IEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/{userId}/students")
    @Operation(summary = "Lista paginada de estudiantes del docente con sus enrollments. offset=pagina (1-indexed), limit=cantidad")
    public ResponseEntity<PagedResponse<TeacherStudentResponse>> students(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
        @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String sort
    ) {
        Sort.Direction dir = "desc".equalsIgnoreCase(sort) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(offset - 1, limit, Sort.by(dir, "lastNames", "names"));
        Page<TeacherStudent> page = enrollmentService.studentsOfTeacher(userId, pageable);
        return ResponseEntity.ok(PagedResponse.of(page.map(TeacherStudentResponse::from)));
    }
}
