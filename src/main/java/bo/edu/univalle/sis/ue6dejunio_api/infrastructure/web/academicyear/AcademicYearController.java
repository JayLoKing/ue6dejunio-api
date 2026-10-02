package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.academicyear;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.AcademicYearResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PutMapping("/{id}/activate")
    @Operation(summary = "Activar una gestión (la gestión de trabajo del sistema)")
    public ResponseEntity<AcademicYearResponse> activate(@PathVariable int id) {
        return ResponseEntity.ok(AcademicYearResponse.from(academicYearService.activate(id)));
    }
}
