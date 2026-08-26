package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "AssessmentScores", description = "Notas por estudiante y criterio. Consolida academic_scores")
@SecurityRequirement(name = "bearerAuth")
public class AssessmentScoreController {

    private final IAssessmentScoreService scoreService;

    public AssessmentScoreController(IAssessmentScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @PostMapping
    @PreAuthorize("@authz.canWriteScoreTarget(authentication, #r.eventId(), #r.criterionId())")
    @Operation(summary = "Registrar/actualizar nota sobre un criterio de actividad o un criterio directo")
    public ResponseEntity<AssessmentScoreResponse> setScore(@Valid @RequestBody SetScoreRequest r,
                                                           JwtAuthenticationToken token) {
        AssessmentScore saved = scoreService.setScore(new SetScoreCommand(
            r.courseEnrollmentId(), r.eventId(), r.criterionId(), r.score(), currentUserId(token)));
        return ResponseEntity.ok(AssessmentScoreResponse.from(saved));
    }

    /**
     * Author of the score, parsed defensively. A Director clears {@code @PreAuthorize} before the
     * subject is ever read, so a malformed one reaches this point: parsing it raw would report the
     * caller's bad token as a server failure.
     */
    private UUID currentUserId(JwtAuthenticationToken token) {
        String subject = token != null && token.getToken() != null
            ? token.getToken().getSubject()
            : null;
        if (subject == null) {
            throw new ValidationException("El token no identifica al usuario");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("El token no identifica al usuario");
        }
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("@authz.canReadScoreEvent(authentication, #eventId)")
    @Operation(summary = "Notas de todos los estudiantes en un criterio de actividad")
    public ResponseEntity<List<AssessmentScoreResponse>> byEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok(scoreService.listByEvent(eventId).stream()
            .map(AssessmentScoreResponse::from).toList());
    }

    @GetMapping("/criterion/{criterionId}")
    @PreAuthorize("@authz.canReadScoreCriterion(authentication, #criterionId)")
    @Operation(summary = "Notas directas de todos los estudiantes en un criterio")
    public ResponseEntity<List<AssessmentScoreResponse>> byCriterion(@PathVariable UUID criterionId) {
        return ResponseEntity.ok(scoreService.listByCriterion(criterionId).stream()
            .map(AssessmentScoreResponse::from).toList());
    }

    @GetMapping
    @PreAuthorize("@authz.canReadEnrollmentScope(authentication, #courseEnrollmentId)")
    @Operation(summary = "Notas de un estudiante (por course_enrollment)")
    public ResponseEntity<List<AssessmentScoreResponse>> byCourseEnrollment(
        @RequestParam("id_course_enrollment") UUID courseEnrollmentId) {
        return ResponseEntity.ok(scoreService.listByCourseEnrollment(courseEnrollmentId).stream()
            .map(AssessmentScoreResponse::from).toList());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canWriteScore(authentication, #id)")
    @Operation(summary = "Eliminar nota (consolida)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        scoreService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
