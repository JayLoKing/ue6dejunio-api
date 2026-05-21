package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.score;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.AcademicScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.score.RegisterScoreCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.RegisterScoreRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ScoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/scores")
@Tag(name = "Scores", description = "Cuaderno pedagogico: notas academicas")
@SecurityRequirement(name = "bearerAuth")
public class ScoreController {

    private final IScoreService scoreService;

    public ScoreController(IScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @PostMapping
    @Operation(summary = "Registrar/actualizar notas de una inscripcion por trimestre (total auto)")
    public ResponseEntity<ScoreResponse> register(@Valid @RequestBody RegisterScoreRequest request,
                                                  JwtAuthenticationToken token) {
        UUID createdBy = UUID.fromString(token.getToken().getSubject());
        AcademicScore saved = scoreService.register(new RegisterScoreCommand(
            request.enrollmentId(), request.trimester(),
            request.scoreBeing(), request.scoreKnowing(), request.scoreDoing(), request.scoreDeciding(),
            createdBy));
        return ResponseEntity.ok(ScoreResponse.from(saved));
    }

    @GetMapping("/enrollment/{enrollmentId}")
    @Operation(summary = "Listar notas de una inscripcion (3 trimestres)")
    public ResponseEntity<List<ScoreResponse>> byEnrollment(@PathVariable UUID enrollmentId) {
        List<ScoreResponse> body = scoreService.byEnrollment(enrollmentId).stream()
            .map(ScoreResponse::from).toList();
        return ResponseEntity.ok(body);
    }
}
