package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IGradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CourseAttendanceResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/gradebook")
@Tag(name = "Gradebook", description = "Consolidados: resumen estudiante, centralizador, asistencia")
@SecurityRequirement(name = "bearerAuth")
public class GradebookController {

    private final IGradebookService gradebookService;

    public GradebookController(IGradebookService gradebookService) {
        this.gradebookService = gradebookService;
    }

    @GetMapping("/student-summary")
    @Operation(summary = "Resumen del estudiante: total por materia + promedio general del trimestre")
    public ResponseEntity<StudentSummaryResponse> studentSummary(
        @RequestParam("id_course_enrollment") UUID courseEnrollmentId,
        @RequestParam @Min(1) @Max(3) Integer trimester
    ) {
        return ResponseEntity.ok(StudentSummaryResponse.from(
            gradebookService.studentSummary(courseEnrollmentId, trimester)));
    }

    @GetMapping("/centralizer")
    @Operation(summary = "Centralizador del curso: todos los estudiantes con totales + promedio general")
    public ResponseEntity<PagedResponse<StudentSummaryResponse>> centralizer(
        @RequestParam("id_course") UUID courseId,
        @RequestParam @Min(1) @Max(3) Integer trimester,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit
    ) {
        Pageable p = PageRequest.of(offset - 1, limit, Sort.by("student.lastNames", "student.names"));
        return ResponseEntity.ok(PagedResponse.of(
            gradebookService.centralizer(courseId, trimester, p).map(StudentSummaryResponse::from)));
    }

    @GetMapping("/attendance")
    @Operation(summary = "Asistencia diaria del curso. date opcional filtra una fecha")
    public ResponseEntity<PagedResponse<CourseAttendanceResponse>> attendance(
        @RequestParam("id_course") UUID courseId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit
    ) {
        Pageable p = PageRequest.of(offset - 1, limit, Sort.by("student.lastNames", "student.names"));
        return ResponseEntity.ok(PagedResponse.of(
            gradebookService.courseAttendance(courseId, date, p).map(CourseAttendanceResponse::from)));
    }
}
