package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.score;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ScoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/scores")
@Tag(name = "Scores", description = "Consolidado por dimension (derivado, solo lectura)")
@SecurityRequirement(name = "bearerAuth")
public class ScoreController {

    private final IScoreService scoreService;

    public ScoreController(IScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @GetMapping
    @PreAuthorize("@authz.canReadEnrollmentScope(authentication, #courseEnrollmentId)")
    @Operation(summary = "Consolidados de un course_enrollment (todas las materias/trimestres)")
    public ResponseEntity<List<ScoreResponse>> byCourseEnrollment(
        @RequestParam("id_course_enrollment") UUID courseEnrollmentId) {
        return ResponseEntity.ok(scoreService.byCourseEnrollment(courseEnrollmentId).stream()
            .map(ScoreResponse::from).toList());
    }
}
