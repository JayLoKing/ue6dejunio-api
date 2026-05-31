package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IGradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CourseAttendanceResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CourseScoreResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/gradebook")
@Tag(name = "Gradebook", description = "Vistas del cuaderno: estudiantes + asistencias / notas")
@SecurityRequirement(name = "bearerAuth")
public class GradebookController {

    private final IGradebookService gradebookService;

    public GradebookController(IGradebookService gradebookService) {
        this.gradebookService = gradebookService;
    }

    @GetMapping("/attendance")
    @Operation(summary = "Estudiantes del curso (grado+paralelo) con sus asistencias. date opcional filtra una fecha")
    public ResponseEntity<PagedResponse<CourseAttendanceResponse>> attendance(
        @RequestParam("id_grade") Integer gradeId,
        @RequestParam("id_parallel") Integer parallelId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit,
        @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String sort
    ) {
        Sort.Direction dir = "desc".equalsIgnoreCase(sort) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable p = PageRequest.of(offset - 1, limit, Sort.by(dir, "student.lastNames", "student.names"));
        return ResponseEntity.ok(PagedResponse.of(
            gradebookService.courseAttendance(gradeId, parallelId, date, p)
                .map(CourseAttendanceResponse::from)));
    }

    @GetMapping("/scores")
    @Operation(summary = "Estudiantes de una materia (class_group) con sus notas. trimester opcional filtra")
    public ResponseEntity<PagedResponse<CourseScoreResponse>> scores(
        @RequestParam("id_class_group") UUID classGroupId,
        @RequestParam(required = false) @Min(1) @Max(3) Integer trimester,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit,
        @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String sort
    ) {
        Sort.Direction dir = "desc".equalsIgnoreCase(sort) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable p = PageRequest.of(offset - 1, limit, Sort.by(dir, "student.lastNames", "student.names"));
        return ResponseEntity.ok(PagedResponse.of(
            gradebookService.classGroupScores(classGroupId, trimester, p)
                .map(CourseScoreResponse::from)));
    }
}
