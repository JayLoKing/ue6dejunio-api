package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.grade;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.CreateGradeCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.Grade;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.UpdateGradeCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.grade.IGradeService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateGradeRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.GradeResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateGradeRequest;
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
@RequestMapping("/api/grades")
@Tag(name = "Grades", description = "Grados (Director). Max 6 (primaria)")
@SecurityRequirement(name = "bearerAuth")
public class GradeController {

    private final IGradeService gradeService;

    public GradeController(IGradeService gradeService) {
        this.gradeService = gradeService;
    }

    @PostMapping
    @Operation(summary = "Crear grado (max 6 total)")
    public ResponseEntity<GradeResponse> create(@Valid @RequestBody CreateGradeRequest r) {
        Grade g = gradeService.create(new CreateGradeCommand(r.name(), r.levelId()));
        return ResponseEntity.ok(GradeResponse.from(g));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener grado por id")
    public ResponseEntity<GradeResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(GradeResponse.from(gradeService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Listar grados. offset=pagina (1-indexed), limit=cantidad")
    public ResponseEntity<PagedResponse<GradeResponse>> list(
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
        @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String sort
    ) {
        Sort.Direction dir = "desc".equalsIgnoreCase(sort) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable p = PageRequest.of(offset - 1, limit, Sort.by(dir, "name"));
        return ResponseEntity.ok(PagedResponse.of(gradeService.list(p).map(GradeResponse::from)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar grado")
    public ResponseEntity<GradeResponse> update(@PathVariable Integer id,
                                                @Valid @RequestBody UpdateGradeRequest r) {
        Grade g = gradeService.update(id, new UpdateGradeCommand(r.name(), r.levelId()));
        return ResponseEntity.ok(GradeResponse.from(g));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar grado (falla si tiene class_groups)")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        gradeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
