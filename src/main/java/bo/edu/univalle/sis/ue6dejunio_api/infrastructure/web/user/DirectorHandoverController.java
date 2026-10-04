package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.user;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.DirectorHandoverDetail;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.StartDirectorHandoverCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.handover.IDirectorHandoverService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.DirectorHandoverResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StartDirectorHandoverRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The handover of the Director role. Under {@code /api/users/**}, so {@code SecurityConfig} already
 * limits it to Directors; who may cancel is the service's rule, because both Directors pass the
 * filter and only the outgoing one may call it off.
 *
 * <p>The literal path wins over {@code /api/users/{id}} for GET and DELETE, so these never reach
 * the by-id handlers.
 */
@RestController
@RequestMapping("/api/users/director-handover")
@Tag(name = "Director handover", description = "Cambio de Director (solo Director)")
@SecurityRequirement(name = "bearerAuth")
public class DirectorHandoverController {

    private final IDirectorHandoverService handoverService;

    public DirectorHandoverController(IDirectorHandoverService handoverService) {
        this.handoverService = handoverService;
    }

    @PostMapping
    @Operation(
            summary =
                    "Iniciar el cambio de Director: crea o asciende al nuevo Director y le envía"
                            + " credenciales temporales")
    public ResponseEntity<DirectorHandoverResponse> start(
            @Valid @RequestBody StartDirectorHandoverRequest request,
            JwtAuthenticationToken token) {
        DirectorHandoverDetail detail =
                handoverService.start(
                        new StartDirectorHandoverCommand(
                                callerId(token),
                                request.email(),
                                request.ci(),
                                request.names(),
                                request.lastNames(),
                                request.phone()));
        return ResponseEntity.created(URI.create("/api/users/director-handover"))
                .body(DirectorHandoverResponse.from(detail));
    }

    @GetMapping
    @Operation(summary = "Cambio de Director en curso; 204 si no hay ninguno")
    public ResponseEntity<DirectorHandoverResponse> pending() {
        return handoverService
                .findPending()
                .map(DirectorHandoverResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping
    @Operation(summary = "Cancelar el cambio de Director en curso (solo el Director saliente)")
    public ResponseEntity<Void> cancel(JwtAuthenticationToken token) {
        handoverService.cancel(callerId(token));
        return ResponseEntity.noContent().build();
    }

    private static UUID callerId(JwtAuthenticationToken token) {
        return UUID.fromString(token.getToken().getSubject());
    }
}
