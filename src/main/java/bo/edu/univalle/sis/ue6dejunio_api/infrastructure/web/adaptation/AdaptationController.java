package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.adaptation;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.UpdateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.AdaptationResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateAdaptationRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateAdaptationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/adaptations")
@Tag(name = "Adaptations", description = "Adaptaciones curriculares por estudiante de un PDC")
@SecurityRequirement(name = "bearerAuth")
public class AdaptationController {

    private final IAdaptationService adaptationService;

    public AdaptationController(IAdaptationService adaptationService) {
        this.adaptationService = adaptationService;
    }

    private static UUID currentUser(JwtAuthenticationToken token) {
        return UUID.fromString(token.getToken().getSubject());
    }

    @PostMapping
    @Operation(summary = "Crear adaptacion curricular (unica por plan + estudiante)")
    public ResponseEntity<AdaptationResponse> create(@Valid @RequestBody CreateAdaptationRequest r,
                                                     JwtAuthenticationToken token) {
        Adaptation a = adaptationService.create(new CreateAdaptationCommand(
            r.planId(), r.studentId(), r.adaptedContents(), r.adaptedMethodology(),
            r.adaptedCriteria(), currentUser(token)));
        return ResponseEntity.ok(AdaptationResponse.from(a));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener adaptacion por id")
    public ResponseEntity<AdaptationResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(AdaptationResponse.from(adaptationService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Listar adaptaciones de un PDC")
    public ResponseEntity<PagedResponse<AdaptationResponse>> list(
        @RequestParam("id_curriculum_plan") UUID planId,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit
    ) {
        Pageable p = PageRequest.of(offset - 1, limit);
        return ResponseEntity.ok(PagedResponse.of(
            adaptationService.listByPlan(planId, p).map(AdaptationResponse::from)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar adaptacion")
    public ResponseEntity<AdaptationResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateAdaptationRequest r,
                                                    JwtAuthenticationToken token) {
        Adaptation a = adaptationService.update(id, new UpdateAdaptationCommand(
            r.adaptedContents(), r.adaptedMethodology(), r.adaptedCriteria(), currentUser(token)));
        return ResponseEntity.ok(AdaptationResponse.from(a));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar adaptacion")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adaptationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
