package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.knowledgearea;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.CreateKnowledgeAreaCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.KnowledgeArea;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.UpdateKnowledgeAreaCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.knowledgearea.IKnowledgeAreaService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateKnowledgeAreaRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.KnowledgeAreaResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateKnowledgeAreaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/knowledge-areas")
@Tag(name = "KnowledgeAreas", description = "Areas de saberes: agrupan a las materias (Director)")
@SecurityRequirement(name = "bearerAuth")
public class KnowledgeAreaController {

    private final IKnowledgeAreaService areaService;

    public KnowledgeAreaController(IKnowledgeAreaService areaService) {
        this.areaService = areaService;
    }

    @PostMapping
    @Operation(summary = "Crear area de saberes. displayOrder opcional: por defecto va al final")
    public ResponseEntity<KnowledgeAreaResponse> create(
            @Valid @RequestBody CreateKnowledgeAreaRequest r) {
        KnowledgeArea a =
                areaService.create(new CreateKnowledgeAreaCommand(r.name(), r.displayOrder()));
        return ResponseEntity.ok(KnowledgeAreaResponse.from(a));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener area de saberes por id")
    public ResponseEntity<KnowledgeAreaResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(KnowledgeAreaResponse.from(areaService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Listar areas de saberes, en el orden en que se imprimen")
    public ResponseEntity<PagedResponse<KnowledgeAreaResponse>> list(
            @RequestParam(defaultValue = "1") @Min(1) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        // Sorted by the order they print in: the catalogue exists to lay out the plan, and any
        // other order would make the list disagree with the document it describes.
        PageQuery p = PageQuery.of(offset - 1, limit, SortField.asc("displayOrder"));
        return ResponseEntity.ok(
                PagedResponse.of(areaService.list(p).map(KnowledgeAreaResponse::from)));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar area de saberes. displayOrder opcional: por defecto no se mueve")
    public ResponseEntity<KnowledgeAreaResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateKnowledgeAreaRequest r) {
        KnowledgeArea a =
                areaService.update(id, new UpdateKnowledgeAreaCommand(r.name(), r.displayOrder()));
        return ResponseEntity.ok(KnowledgeAreaResponse.from(a));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar area de saberes. Rechazado si tiene materias asociadas")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        areaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
