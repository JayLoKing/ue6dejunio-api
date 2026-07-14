package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.catalog;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.GradeItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.ParallelItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.SubjectItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.TeacherItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.catalog.ICatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@Tag(name = "Catalog", description = "Listas para dropdowns/checkboxes")
@SecurityRequirement(name = "bearerAuth")
public class CatalogController {

    private final ICatalogService catalogService;

    public CatalogController(ICatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/subjects")
    @Operation(summary = "Listar materias activas. technical opcional: true=tecnicas, false=no tecnicas")
    public ResponseEntity<List<SubjectItem>> subjects(
        @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean technical) {
        return ResponseEntity.ok(catalogService.subjects(technical));
    }

    @GetMapping("/grades")
    @Operation(summary = "Listar grados")
    public ResponseEntity<List<GradeItem>> grades() {
        return ResponseEntity.ok(catalogService.grades());
    }

    @GetMapping("/parallels")
    @Operation(summary = "Listar paralelos")
    public ResponseEntity<List<ParallelItem>> parallels() {
        return ResponseEntity.ok(catalogService.parallels());
    }

    @GetMapping("/teachers")
    @Operation(summary = "Listar docentes activos. technical opcional: true=tecnicos, false=no tecnicos")
    public ResponseEntity<List<TeacherItem>> teachers(
        @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean technical) {
        return ResponseEntity.ok(catalogService.teachers(technical));
    }
}
