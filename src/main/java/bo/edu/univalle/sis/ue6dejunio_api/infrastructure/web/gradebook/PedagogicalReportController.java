package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.gradebook;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IPedagogicalReportService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PedagogicalReportResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.SavePedagogicalReportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The informe pedagógico, kept apart from {@link GradebookController}.
 *
 * <p>Same {@code /api/gradebook} prefix, because that is where the school's reports live and the
 * security chain reads a route's reach from its path. Its own class because this is the only one of
 * them a person writes: it has a body, a verb that is not GET, and an owner the other five do not.
 */
@RestController
@Validated
@RequestMapping("/api/gradebook/pedagogical-report")
@Tag(
        name = "Gradebook",
        description =
                "Informe pedagogico del curso por trimestre: datos "
                        + "referenciales, logros y dificultades, estadistica y cuadro de estudiantes reprobados")
@SecurityRequirement(name = "bearerAuth")
public class PedagogicalReportController {

    private final IPedagogicalReportService pedagogicalReportService;

    public PedagogicalReportController(IPedagogicalReportService pedagogicalReportService) {
        this.pedagogicalReportService = pedagogicalReportService;
    }

    @GetMapping
    @PreAuthorize("@authz.canReadCourse(authentication, #courseId)")
    @Operation(
            summary =
                    "Informe pedagogico del curso en un trimestre: datos del curso, logros y "
                            + "dificultades, estadistica de aprobados y reprobados, y el cuadro de estudiantes "
                            + "reprobados con sus areas. Devuelve la hoja completa aunque nadie la haya escrito aun")
    public ResponseEntity<PedagogicalReportResponse> sheet(
            @RequestParam("id_course") UUID courseId,
            @RequestParam @Min(1) @Max(3) Integer trimester) {
        return ResponseEntity.ok(
                PedagogicalReportResponse.from(
                        pedagogicalReportService.sheet(courseId, trimester)));
    }

    /**
     * PUT and not POST: there is one informe per course and trimester, {@code
     * uq_pedagogical_report} says so, and the caller names it in the query rather than learning an
     * id back. Saving twice leaves the same document, which is what makes this idempotent and the
     * verb right.
     */
    @PutMapping
    @PreAuthorize("@authz.canWritePedagogicalReport(authentication, #courseId)")
    @Operation(
            summary =
                    "Guarda lo que el docente escribe del informe: logros, dificultades y las "
                            + "acciones y fuente de verificacion de cada estudiante reprobado. Reemplaza el documento "
                            + "completo y responde con la hoja ya armada")
    public ResponseEntity<PedagogicalReportResponse> save(
            @RequestParam("id_course") UUID courseId,
            @RequestParam @Min(1) @Max(3) Integer trimester,
            @Valid @RequestBody SavePedagogicalReportRequest request) {
        return ResponseEntity.ok(
                PedagogicalReportResponse.from(
                        pedagogicalReportService.save(courseId, trimester, request.toDraft())));
    }
}
