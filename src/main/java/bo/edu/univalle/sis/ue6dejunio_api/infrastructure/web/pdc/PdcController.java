package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpdatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.CreateProgressCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.progress.IProgressService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreatePdcRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ObservePdcRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateProgressRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PdcResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ProgressResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdatePdcRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security.AuthorizationComponent;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/pdc")
@Tag(name = "PDC", description = "Plan de Desarrollo Curricular. Teacher crea/edita/publica, Director aprueba/observa")
@SecurityRequirement(name = "bearerAuth")
public class PdcController {

    private final IPdcService pdcService;
    private final IProgressService progressService;
    private final AuthorizationComponent authz;

    public PdcController(IPdcService pdcService, IProgressService progressService,
                         AuthorizationComponent authz) {
        this.pdcService = pdcService;
        this.progressService = progressService;
        this.authz = authz;
    }

    @PostMapping
    @PreAuthorize("@authz.canWriteClassGroup(authentication, #r.classGroupId())")
    @Operation(summary = "Crear PDC (estado Draft). Unico por class_group + trimestre")
    public ResponseEntity<PdcResponse> create(@Valid @RequestBody CreatePdcRequest r,
                                              JwtAuthenticationToken token) {
        UUID userId = UUID.fromString(token.getToken().getSubject());
        Pdc created = pdcService.create(CreatePdcCommand.builder()
            .classGroupId(r.classGroupId()).trimester(r.trimester()).title(r.title())
            .holisticObjective(r.holisticObjective()).learningObjective(r.learningObjective())
            .contents(r.contents()).practiceActivities(r.practiceActivities())
            .theoryActivities(r.theoryActivities()).valuationActivities(r.valuationActivities())
            .productionActivities(r.productionActivities()).resources(r.resources())
            .startDate(r.startDate()).endDate(r.endDate())
            .criteriaBeing(r.criteriaBeing()).criteriaKnowing(r.criteriaKnowing())
            .criteriaDoing(r.criteriaDoing()).criteriaDeciding(r.criteriaDeciding())
            .build(), userId);
        return ResponseEntity.ok(PdcResponse.from(created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canReadPdc(authentication, #id)")
    @Operation(summary = "Obtener PDC por id")
    public ResponseEntity<PdcResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(PdcResponse.from(pdcService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Listar PDC. Filtros opcionales: id_class_group, trimester, status")
    public ResponseEntity<PagedResponse<PdcResponse>> list(
        @RequestParam(value = "id_class_group", required = false) UUID classGroupId,
        @RequestParam(required = false) @Min(1) @Max(3) Integer trimester,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
        Authentication authentication
    ) {
        PageQuery p = PageQuery.of(offset - 1, limit, SortField.desc("updatedAt"));
        // The filter is optional, so the scope is what keeps a Teacher inside their own plans.
        UUID scope = authz.pdcListScopeTeacherId(authentication);
        return ResponseEntity.ok(PagedResponse.of(
            pdcService.list(classGroupId, trimester, status, scope, p).map(PdcResponse::from)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canWritePdc(authentication, #id)")
    @Operation(summary = "Actualizar PDC (solo Draft o With Observations)")
    public ResponseEntity<PdcResponse> update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdatePdcRequest r,
                                             JwtAuthenticationToken token) {
        UUID userId = UUID.fromString(token.getToken().getSubject());
        Pdc updated = pdcService.update(id, UpdatePdcCommand.builder()
            .title(r.title()).holisticObjective(r.holisticObjective())
            .learningObjective(r.learningObjective()).contents(r.contents())
            .practiceActivities(r.practiceActivities()).theoryActivities(r.theoryActivities())
            .valuationActivities(r.valuationActivities()).productionActivities(r.productionActivities())
            .resources(r.resources()).startDate(r.startDate()).endDate(r.endDate())
            .criteriaBeing(r.criteriaBeing()).criteriaKnowing(r.criteriaKnowing())
            .criteriaDoing(r.criteriaDoing()).criteriaDeciding(r.criteriaDeciding())
            .build(), userId);
        return ResponseEntity.ok(PdcResponse.from(updated));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@authz.canWritePdc(authentication, #id)")
    @Operation(summary = "Publicar PDC para revision (Teacher). Draft/With Observations -> Published")
    public ResponseEntity<PdcResponse> publish(@PathVariable UUID id, JwtAuthenticationToken token) {
        UUID userId = UUID.fromString(token.getToken().getSubject());
        return ResponseEntity.ok(PdcResponse.from(pdcService.publish(id, userId)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('Director')")
    @Operation(summary = "Aprobar PDC (Director). Published/Under Review -> Approved")
    public ResponseEntity<PdcResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(PdcResponse.from(pdcService.approve(id)));
    }

    @PostMapping("/{id}/observe")
    @PreAuthorize("hasRole('Director')")
    @Operation(summary = "Observar PDC (Director). Published/Under Review -> With Observations")
    public ResponseEntity<PdcResponse> observe(@PathVariable UUID id,
                                              @Valid @RequestBody ObservePdcRequest r) {
        return ResponseEntity.ok(PdcResponse.from(pdcService.observe(id, r.observations())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canWritePdc(authentication, #id)")
    @Operation(summary = "Eliminar PDC (solo Draft)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        pdcService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/progress")
    @PreAuthorize("@authz.canWritePdc(authentication, #id)")
    @Operation(summary = "Registrar avance del PDC (PROG AV)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ProgressResponse> addProgress(@PathVariable UUID id,
                                                       @Valid @RequestBody CreateProgressRequest r,
                                                       JwtAuthenticationToken token) {
        UUID userId = UUID.fromString(token.getToken().getSubject());
        return ResponseEntity.ok(ProgressResponse.from(progressService.create(new CreateProgressCommand(
            id, r.progressDate(), r.advancedContent(), r.percentage(), r.observations(), userId))));
    }

    @GetMapping("/{id}/progress")
    @PreAuthorize("@authz.canReadPdc(authentication, #id)")
    @Operation(summary = "Listar avances del PDC", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<ProgressResponse>> progress(@PathVariable UUID id) {
        return ResponseEntity.ok(progressService.listByPlan(id).stream().map(ProgressResponse::from).toList());
    }
}
