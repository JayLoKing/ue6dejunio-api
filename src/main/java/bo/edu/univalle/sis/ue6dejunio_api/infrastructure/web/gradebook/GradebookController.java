package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IGradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CourseAttendanceResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentAnnualSummaryResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentReportCardResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("@authz.canReadEnrollmentScope(authentication, #courseEnrollmentId)")
    @Operation(summary = "Resumen del estudiante: total por materia + promedio general del trimestre")
    public ResponseEntity<StudentSummaryResponse> studentSummary(
        @RequestParam("id_course_enrollment") UUID courseEnrollmentId,
        @RequestParam @Min(1) @Max(3) Integer trimester
    ) {
        return ResponseEntity.ok(StudentSummaryResponse.from(
            gradebookService.studentSummary(courseEnrollmentId, trimester)));
    }

    @GetMapping("/centralizer")
    @PreAuthorize("@authz.canReadCourse(authentication, #courseId)")
    @Operation(summary = "Centralizador del curso: todos los estudiantes con totales + promedio general")
    public ResponseEntity<PagedResponse<StudentSummaryResponse>> centralizer(
        @RequestParam("id_course") UUID courseId,
        @RequestParam @Min(1) @Max(3) Integer trimester,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit
    ) {
        PageQuery p = PageQuery.of(offset - 1, limit, SortField.asc("student.lastNames"), SortField.asc("student.names"));
        return ResponseEntity.ok(PagedResponse.of(
            gradebookService.centralizer(courseId, trimester, p).map(StudentSummaryResponse::from)));
    }

    @GetMapping("/report-card")
    @PreAuthorize("@authz.canReadEnrollmentScope(authentication, #courseEnrollmentId)")
    @Operation(summary = "Libreta del estudiante: areas agrupadas por campo de saberes, promedio "
        + "anual en numeral y literal, y areas aprobadas y reprobadas por trimestre")
    public ResponseEntity<StudentReportCardResponse> reportCard(
        @RequestParam("id_course_enrollment") UUID courseEnrollmentId
    ) {
        return ResponseEntity.ok(StudentReportCardResponse.from(
            gradebookService.reportCard(courseEnrollmentId)));
    }

    @GetMapping("/annual-centralizer")
    @PreAuthorize("@authz.canReadCourse(authentication, #courseId)")
    @Operation(summary = "Centralizador anual del curso: por area los tres trimestres y su "
        + "promedio, mas los promedios trimestrales y el promedio final")
    public ResponseEntity<PagedResponse<StudentAnnualSummaryResponse>> annualCentralizer(
        @RequestParam("id_course") UUID courseId,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit
    ) {
        // No trimester and no gestión: the course already names its academic year, and the sheet
        // is the whole year by definition.
        PageQuery p = PageQuery.of(offset - 1, limit, SortField.asc("student.lastNames"), SortField.asc("student.names"));
        return ResponseEntity.ok(PagedResponse.of(
            gradebookService.annualCentralizer(courseId, p).map(StudentAnnualSummaryResponse::from)));
    }

    @GetMapping("/attendance")
    @PreAuthorize("@authz.canReadCourse(authentication, #courseId)")
    @Operation(summary = "Asistencia diaria del curso. date opcional filtra una fecha")
    public ResponseEntity<PagedResponse<CourseAttendanceResponse>> attendance(
        @RequestParam("id_course") UUID courseId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit
    ) {
        PageQuery p = PageQuery.of(offset - 1, limit, SortField.asc("student.lastNames"), SortField.asc("student.names"));
        return ResponseEntity.ok(PagedResponse.of(
            gradebookService.courseAttendance(courseId, date, p).map(CourseAttendanceResponse::from)));
    }

    @GetMapping("/attendance/session")
    @PreAuthorize("@authz.canReadClassGroup(authentication, #classGroupId)")
    @Operation(summary = "Asistencia de una materia. Lista de estudiantes del curso con las "
        + "sesiones de esa materia. date opcional filtra una fecha")
    public ResponseEntity<PagedResponse<CourseAttendanceResponse>> classGroupAttendance(
        @RequestParam("id_class_group") UUID classGroupId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit
    ) {
        PageQuery p = PageQuery.of(offset - 1, limit, SortField.asc("student.lastNames"), SortField.asc("student.names"));
        return ResponseEntity.ok(PagedResponse.of(
            gradebookService.classGroupAttendance(classGroupId, date, p)
                .map(CourseAttendanceResponse::from)));
    }
}
