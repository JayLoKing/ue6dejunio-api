package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.level;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.CreateLevelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.Level;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.UpdateLevelCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.level.ILevelService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateLevelRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.LevelResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateLevelRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/levels")
@Tag(name = "Levels", description = "Niveles educativos (Director)")
@SecurityRequirement(name = "bearerAuth")
public class LevelController {

    private final ILevelService levelService;

    public LevelController(ILevelService levelService) {
        this.levelService = levelService;
    }

    @PostMapping
    @Operation(summary = "Crear nivel")
    public ResponseEntity<LevelResponse> create(@Valid @RequestBody CreateLevelRequest request) {
        Level created = levelService.create(new CreateLevelCommand(request.name()));
        return ResponseEntity.ok(LevelResponse.from(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener nivel por id")
    public ResponseEntity<LevelResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(LevelResponse.from(levelService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Listar niveles. offset=pagina (1-indexed), limit=cantidad")
    public ResponseEntity<PagedResponse<LevelResponse>> list(
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
        @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String sort
    ) {
        Sort.Direction dir = "desc".equalsIgnoreCase(sort) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable p = PageRequest.of(offset - 1, limit, Sort.by(dir, "name"));
        return ResponseEntity.ok(PagedResponse.of(levelService.list(p).map(LevelResponse::from)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar nivel")
    public ResponseEntity<LevelResponse> update(@PathVariable Integer id,
                                                @Valid @RequestBody UpdateLevelRequest request) {
        Level updated = levelService.update(id, new UpdateLevelCommand(request.name()));
        return ResponseEntity.ok(LevelResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar nivel (falla si tiene grados)")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        levelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
