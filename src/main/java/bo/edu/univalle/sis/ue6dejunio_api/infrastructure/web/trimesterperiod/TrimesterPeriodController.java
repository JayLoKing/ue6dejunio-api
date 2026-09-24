package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.trimesterperiod;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.CreateTrimesterPeriodCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.UpdateTrimesterPeriodCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateTrimesterPeriodRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.TrimesterPeriodResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateTrimesterPeriodRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/trimester-periods")
@Tag(
        name = "Trimester Periods",
        description = "Rangos de fecha de trimestre por anio academico (Director)")
@SecurityRequirement(name = "bearerAuth")
public class TrimesterPeriodController {

    private final ITrimesterPeriodService trimesterPeriodService;

    public TrimesterPeriodController(ITrimesterPeriodService trimesterPeriodService) {
        this.trimesterPeriodService = trimesterPeriodService;
    }

    @PostMapping
    @Operation(summary = "Configurar un periodo de trimestre para un anio academico")
    public ResponseEntity<TrimesterPeriodResponse> create(
            @Valid @RequestBody CreateTrimesterPeriodRequest r) {
        TrimesterPeriod created =
                trimesterPeriodService.create(
                        new CreateTrimesterPeriodCommand(
                                r.academicYearId(), r.trimester(), r.startDate(), r.endDate()));
        return ResponseEntity.ok(TrimesterPeriodResponse.from(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener periodo de trimestre por id")
    public ResponseEntity<TrimesterPeriodResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(TrimesterPeriodResponse.from(trimesterPeriodService.getById(id)));
    }

    @GetMapping
    @Operation(
            summary =
                    "Listar periodos configurados de un anio academico (id_academic_year requerido)")
    public ResponseEntity<List<TrimesterPeriodResponse>> list(
            @RequestParam("id_academic_year") @NotNull Integer academicYearId) {
        List<TrimesterPeriodResponse> body =
                trimesterPeriodService.listByAcademicYear(academicYearId).stream()
                        .map(TrimesterPeriodResponse::from)
                        .toList();
        return ResponseEntity.ok(body);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar fechas de un periodo de trimestre")
    public ResponseEntity<TrimesterPeriodResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateTrimesterPeriodRequest r) {
        TrimesterPeriod updated =
                trimesterPeriodService.update(
                        id, new UpdateTrimesterPeriodCommand(r.startDate(), r.endDate()));
        return ResponseEntity.ok(TrimesterPeriodResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar periodo de trimestre")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        trimesterPeriodService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
