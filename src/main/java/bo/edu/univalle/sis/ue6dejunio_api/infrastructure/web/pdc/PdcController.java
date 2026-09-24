package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpdatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.CreateProgressCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.progress.IProgressService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security.AuthorizationComponent;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreatePdcRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateProgressRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ObservePdcRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PdcResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ProgressResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdatePdcRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpsertPdcSubjectRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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

@RestController
@Validated
@RequestMapping("/api/pdc")
@Tag(
        name = "PDC",
        description =
                "Plan de Desarrollo Curricular. Teacher crea/edita/publica, Director aprueba/observa")
@SecurityRequirement(name = "bearerAuth")
public class PdcController {

    private final IPdcService pdcService;
    private final IProgressService progressService;
    private final AuthorizationComponent authz;

    public PdcController(
            IPdcService pdcService,
            IProgressService progressService,
            AuthorizationComponent authz) {
        this.pdcService = pdcService;
        this.progressService = progressService;
        this.authz = authz;
    }

    /**
     * The caller's id. A Director's token reaches here without the ownership guard ever parsing its
     * subject — the role short-circuits first — so a non-UUID subject would arrive raw and surface
     * as a 500. Refusing it is the same answer the guards give: denied, not broken.
     */
    private static UUID currentUser(JwtAuthenticationToken token) {
        String subject = token.getToken().getSubject();
        if (subject == null) {
            throw new AccessDeniedException("Token sin sujeto utilizable");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Token sin sujeto utilizable");
        }
    }

    @PostMapping
    @PreAuthorize("@authz.canWritePdcForCourse(authentication, #r.courseId())")
    @Operation(summary = "Crear PDC del mes (estado Draft). Unico por curso + trimestre + numero")
    public ResponseEntity<PdcResponse> create(
            @Valid @RequestBody CreatePdcRequest r, JwtAuthenticationToken token) {
        UUID userId = currentUser(token);
        Pdc created =
                pdcService.create(
                        new CreatePdcCommand(
                                r.courseId(),
                                r.planNumber(),
                                r.trimester(),
                                r.periodStart(),
                                r.periodEnd(),
                                r.holisticObjective(),
                                r.finalProduct(),
                                r.bibliography(),
                                r.classGroupIds()),
                        userId);
        return ResponseEntity.ok(PdcResponse.from(created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canReadPdc(authentication, #id)")
    @Operation(summary = "Obtener PDC por id")
    public ResponseEntity<PdcResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(PdcResponse.from(pdcService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Listar PDC. Filtros opcionales: id_course, trimester, status")
    public ResponseEntity<PagedResponse<PdcResponse>> list(
            @RequestParam(value = "id_course", required = false) UUID courseId,
            @RequestParam(required = false) @Min(1) @Max(3) Integer trimester,
            // A status outside the set is a caller mistake, not an empty page: the filter goes
            // straight
            // into the query, so a typo would read as "no plans" instead of "no such status".
            @RequestParam(required = false)
                    @Pattern(regexp = "Draft|Published|Under Review|Approved|With Observations")
                    String status,
            @RequestParam(defaultValue = "1") @Min(1) int offset,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit,
            Authentication authentication) {
        // updatedAt ties: a rotation stamps the same instant across every copy it makes. Without a
        // tiebreaker the order of tied rows is undefined and a page can repeat or drop one.
        PageQuery p =
                PageQuery.of(offset - 1, limit, SortField.desc("updatedAt"), SortField.asc("id"));
        // The filter is optional, so the scope is what keeps a Teacher inside their own plans.
        // What an unscoped listing may show is the service's rule, not this mapping's.
        UUID scope = authz.pdcListScopeTeacherId(authentication);
        return ResponseEntity.ok(
                PagedResponse.of(
                        pdcService
                                .list(courseId, trimester, status, scope, p)
                                .map(PdcResponse::from)));
    }

    @PutMapping("/{id}")
    // The heading is shared by every subject of the plan — its number, its period, its holistic
    // objective. A specialist writes their own block, not the frame the whole course hangs on.
    @PreAuthorize("@authz.canAdministerPdc(authentication, #id)")
    @Operation(summary = "Actualizar los datos generales del PDC (solo Draft o With Observations)")
    public ResponseEntity<PdcResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePdcRequest r,
            JwtAuthenticationToken token) {
        UUID userId = currentUser(token);
        Pdc updated =
                pdcService.update(
                        id,
                        new UpdatePdcCommand(
                                r.planNumber(),
                                r.periodStart(),
                                r.periodEnd(),
                                r.holisticObjective(),
                                r.finalProduct(),
                                r.bibliography()),
                        userId);
        return ResponseEntity.ok(PdcResponse.from(updated));
    }

    @PutMapping("/{id}/subjects/{planSubjectId}")
    // Reaching the plan is not enough: a specialist owns their own block and nobody else's.
    @PreAuthorize("@authz.canWritePdcSubject(authentication, #id, #planSubjectId)")
    @Operation(
            summary =
                    "Escribir el bloque de una materia del PDC (objetivo, adaptaciones y filas semanales)")
    public ResponseEntity<PdcResponse> writeSubject(
            @PathVariable UUID id,
            @PathVariable UUID planSubjectId,
            @Valid @RequestBody UpsertPdcSubjectRequest r,
            JwtAuthenticationToken token) {
        UUID userId = currentUser(token);
        return ResponseEntity.ok(
                PdcResponse.from(
                        pdcService.writeSubject(id, planSubjectId, r.toCommand(), userId)));
    }

    @PostMapping("/{id}/copy-to-parallels")
    @PreAuthorize("@authz.canAuthorPdc(authentication, #id)")
    @Operation(
            summary =
                    "Copiar el PDC del mes a los otros paralelos del grado, uno en Draft por curso")
    public ResponseEntity<List<PdcResponse>> copyToParallels(
            @PathVariable UUID id, JwtAuthenticationToken token) {
        UUID userId = currentUser(token);
        return ResponseEntity.ok(
                pdcService.copyToSiblingCourses(id, userId).stream()
                        .map(PdcResponse::from)
                        .toList());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("@authz.canAuthorPdc(authentication, #id)")
    @Operation(
            summary = "Publicar PDC para revision (Teacher). Draft/With Observations -> Published")
    public ResponseEntity<PdcResponse> publish(
            @PathVariable UUID id, JwtAuthenticationToken token) {
        UUID userId = currentUser(token);
        return ResponseEntity.ok(PdcResponse.from(pdcService.publish(id, userId)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('Director')")
    @Operation(summary = "Aprobar PDC (Director). Published/Under Review -> Approved")
    public ResponseEntity<PdcResponse> approve(
            @PathVariable UUID id, JwtAuthenticationToken token) {
        UUID userId = currentUser(token);
        return ResponseEntity.ok(PdcResponse.from(pdcService.approve(id, userId)));
    }

    @PostMapping("/{id}/observe")
    @PreAuthorize("hasRole('Director')")
    @Operation(summary = "Observar PDC (Director). Published/Under Review -> With Observations")
    public ResponseEntity<PdcResponse> observe(
            @PathVariable UUID id,
            @Valid @RequestBody ObservePdcRequest r,
            JwtAuthenticationToken token) {
        UUID userId = currentUser(token);
        return ResponseEntity.ok(
                PdcResponse.from(pdcService.observe(id, r.observations(), userId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canAdministerPdc(authentication, #id)")
    @Operation(summary = "Eliminar PDC (solo Draft)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        pdcService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/progress")
    @PreAuthorize("@authz.canWritePdc(authentication, #id)")
    @Operation(
            summary = "Registrar avance del PDC (PROG AV)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ProgressResponse> addProgress(
            @PathVariable UUID id,
            @Valid @RequestBody CreateProgressRequest r,
            JwtAuthenticationToken token) {
        UUID userId = currentUser(token);
        return ResponseEntity.ok(
                ProgressResponse.from(
                        progressService.create(
                                new CreateProgressCommand(
                                        id,
                                        r.progressDate(),
                                        r.advancedContent(),
                                        r.percentage(),
                                        r.observations(),
                                        userId))));
    }

    @GetMapping("/{id}/progress")
    @PreAuthorize("@authz.canReadPdc(authentication, #id)")
    @Operation(
            summary = "Listar avances del PDC",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<ProgressResponse>> progress(@PathVariable UUID id) {
        return ResponseEntity.ok(
                progressService.listByPlan(id).stream().map(ProgressResponse::from).toList());
    }
}
