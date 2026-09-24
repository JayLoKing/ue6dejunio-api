package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.criterion;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.CreateCriterionCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.UpdateCriterionCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateCriterionRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CriterionResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateCriterionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/criteria")
@Tag(
        name = "Criteria",
        description = "Criterios de evaluacion. Directos, o agrupadores de una actividad")
@SecurityRequirement(name = "bearerAuth")
public class CriterionController {

    private final ICriterionService criterionService;

    public CriterionController(ICriterionService criterionService) {
        this.criterionService = criterionService;
    }

    @PostMapping
    @PreAuthorize("@authz.canWriteClassGroup(authentication, #r.classGroupId())")
    @Operation(
            summary =
                    "Crear criterio. Con 'activity' crea tambien sus criterios en la misma transaccion")
    public ResponseEntity<CriterionResponse> create(@Valid @RequestBody CreateCriterionRequest r) {
        EvaluationCriterion c =
                criterionService.create(
                        new CreateCriterionCommand(
                                r.classGroupId(),
                                r.trimester(),
                                r.dimension(),
                                r.name(),
                                r.activityTitle(),
                                r.activityItems(),
                                r.curriculumPlanId()));
        return ResponseEntity.ok(CriterionResponse.from(c));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canReadScoreCriterion(authentication, #id)")
    @Operation(summary = "Obtener criterio por id")
    public ResponseEntity<CriterionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(CriterionResponse.from(criterionService.getById(id)));
    }

    @GetMapping
    @PreAuthorize("@authz.canReadClassGroup(authentication, #classGroupId)")
    @Operation(summary = "Listar criterios de una materia+trimestre. dimension opcional")
    public ResponseEntity<List<CriterionResponse>> list(
            @RequestParam("id_class_group") UUID classGroupId,
            @RequestParam @Min(1) @Max(3) Integer trimester,
            @RequestParam(required = false) String dimension) {
        return ResponseEntity.ok(
                criterionService.list(classGroupId, trimester, dimension).stream()
                        .map(CriterionResponse::from)
                        .toList());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canWriteScoreCriterion(authentication, #id)")
    @Operation(summary = "Actualizar criterio (nombre)")
    public ResponseEntity<CriterionResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateCriterionRequest r) {
        EvaluationCriterion c = criterionService.update(id, new UpdateCriterionCommand(r.name()));
        return ResponseEntity.ok(CriterionResponse.from(c));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canWriteScoreCriterion(authentication, #id)")
    @Operation(summary = "Eliminar criterio (borra sus actividades en cascada)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        criterionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
