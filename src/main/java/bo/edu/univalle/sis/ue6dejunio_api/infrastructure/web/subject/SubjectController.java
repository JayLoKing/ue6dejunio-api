package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.subject;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.CreateSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.UpdateSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.subject.ISubjectService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateSubjectRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.SubjectResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateSubjectRequest;
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

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/subjects")
@Tag(name = "Subjects", description = "Materias (Director)")
@SecurityRequirement(name = "bearerAuth")
public class SubjectController {

    private final ISubjectService subjectService;

    public SubjectController(ISubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @PostMapping
    @Operation(summary = "Crear materia")
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody CreateSubjectRequest r) {
        Subject s = subjectService.create(new CreateSubjectCommand(r.name(), r.area()));
        return ResponseEntity.ok(SubjectResponse.from(s));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener materia por id")
    public ResponseEntity<SubjectResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(SubjectResponse.from(subjectService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Listar materias. offset=pagina (1-indexed), limit=cantidad")
    public ResponseEntity<PagedResponse<SubjectResponse>> list(
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
        @RequestParam(defaultValue = "asc") @Pattern(regexp = "(?i)asc|desc") String sort
    ) {
        Sort.Direction dir = "desc".equalsIgnoreCase(sort) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable p = PageRequest.of(offset - 1, limit, Sort.by(dir, "name"));
        return ResponseEntity.ok(PagedResponse.of(subjectService.list(p).map(SubjectResponse::from)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar materia (nombre, area, active)")
    public ResponseEntity<SubjectResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateSubjectRequest r) {
        Subject s = subjectService.update(id, new UpdateSubjectCommand(r.name(), r.area(), r.active()));
        return ResponseEntity.ok(SubjectResponse.from(s));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete (set is_active=false)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        subjectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
