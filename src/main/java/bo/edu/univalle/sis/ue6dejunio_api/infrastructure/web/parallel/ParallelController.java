package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.parallel;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortDirection;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.CreateParallelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.UpdateParallelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.parallel.IParallelService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateParallelRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ParallelResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateParallelRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
@RequestMapping("/api/parallels")
@Tag(name = "Parallels", description = "Paralelos (Director)")
@SecurityRequirement(name = "bearerAuth")
public class ParallelController {

    private final IParallelService parallelService;

    public ParallelController(IParallelService parallelService) {
        this.parallelService = parallelService;
    }

    @PostMapping
    @Operation(summary = "Crear paralelo")
    public ResponseEntity<ParallelResponse> create(@Valid @RequestBody CreateParallelRequest r) {
        Parallel p = parallelService.create(new CreateParallelCommand(r.name()));
        return ResponseEntity.ok(ParallelResponse.from(p));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener paralelo por id")
    public ResponseEntity<ParallelResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ParallelResponse.from(parallelService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Listar paralelos. offset=pagina (1-indexed), limit=cantidad")
    public ResponseEntity<PagedResponse<ParallelResponse>> list(
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
        @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String sort
    ) {
        SortDirection dir = "desc".equalsIgnoreCase(sort) ? SortDirection.DESC : SortDirection.ASC;
        PageQuery p = PageQuery.of(offset - 1, limit, new SortField("name", dir));
        return ResponseEntity.ok(PagedResponse.of(parallelService.list(p).map(ParallelResponse::from)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar paralelo")
    public ResponseEntity<ParallelResponse> update(@PathVariable Integer id,
                                                   @Valid @RequestBody UpdateParallelRequest r) {
        Parallel p = parallelService.update(id, new UpdateParallelCommand(r.name()));
        return ResponseEntity.ok(ParallelResponse.from(p));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar paralelo (falla si tiene class_groups)")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        parallelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
