package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.academicyear;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.CreatedAcademicYear;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.AcademicYearResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateAcademicYearRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/academic-years")
@Tag(name = "Academic Years", description = "Gestión de trabajo del sistema (Director)")
@SecurityRequirement(name = "bearerAuth")
public class AcademicYearController {

    private final IAcademicYearService academicYearService;

    public AcademicYearController(IAcademicYearService academicYearService) {
        this.academicYearService = academicYearService;
    }

    @PostMapping
    @Operation(
            summary = "Crear una gestión (inactiva y abierta)",
            description = "Idempotente: 201 si la creó, 200 con la gestión existente si ya estaba.")
    public ResponseEntity<AcademicYearResponse> create(
            @Valid @RequestBody CreateAcademicYearRequest r) {
        CreatedAcademicYear result = academicYearService.create(r.year());
        AcademicYearResponse body = AcademicYearResponse.from(result.year());
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(body)
                : ResponseEntity.ok(body);
    }

    @PutMapping("/{id}/close")
    @Operation(summary = "Cerrar una gestión (deja de ser la gestión de trabajo)")
    public ResponseEntity<AcademicYearResponse> close(@PathVariable int id) {
        return ResponseEntity.ok(AcademicYearResponse.from(academicYearService.close(id)));
    }

    @PutMapping("/{id}/reopen")
    @Operation(summary = "Reabrir una gestión cerrada (no la activa)")
    public ResponseEntity<AcademicYearResponse> reopen(@PathVariable int id) {
        return ResponseEntity.ok(AcademicYearResponse.from(academicYearService.reopen(id)));
    }

    @PutMapping("/{id}/activate")
    @Operation(summary = "Activar una gestión (la gestión de trabajo del sistema)")
    public ResponseEntity<AcademicYearResponse> activate(@PathVariable int id) {
        return ResponseEntity.ok(AcademicYearResponse.from(academicYearService.activate(id)));
    }
}
