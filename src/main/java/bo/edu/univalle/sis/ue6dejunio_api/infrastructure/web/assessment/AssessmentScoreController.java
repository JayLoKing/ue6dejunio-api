package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.SetScoreCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.AssessmentScoreResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.SetScoreRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assessment-scores")
@Tag(name = "AssessmentScores", description = "Notas por estudiante por actividad. Consolida academic_scores")
@SecurityRequirement(name = "bearerAuth")
public class AssessmentScoreController {

    private final IAssessmentScoreService scoreService;

    public AssessmentScoreController(IAssessmentScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @PostMapping
    @Operation(summary = "Registrar/actualizar nota (course_enrollment + actividad). Consolida dimension")
    public ResponseEntity<AssessmentScoreResponse> setScore(@Valid @RequestBody SetScoreRequest r,
                                                           JwtAuthenticationToken token) {
        UUID createdBy = UUID.fromString(token.getToken().getSubject());
        AssessmentScore saved = scoreService.setScore(new SetScoreCommand(
            r.courseEnrollmentId(), r.eventId(), r.score(), createdBy));
        return ResponseEntity.ok(AssessmentScoreResponse.from(saved));
    }

    @GetMapping("/event/{eventId}")
    @Operation(summary = "Notas de todos los estudiantes en una actividad")
    public ResponseEntity<List<AssessmentScoreResponse>> byEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok(scoreService.listByEvent(eventId).stream()
            .map(AssessmentScoreResponse::from).toList());
    }

    @GetMapping
    @Operation(summary = "Notas de un estudiante (por course_enrollment)")
    public ResponseEntity<List<AssessmentScoreResponse>> byCourseEnrollment(
        @RequestParam("id_course_enrollment") UUID courseEnrollmentId) {
        return ResponseEntity.ok(scoreService.listByCourseEnrollment(courseEnrollmentId).stream()
            .map(AssessmentScoreResponse::from).toList());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar nota (consolida)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        scoreService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
