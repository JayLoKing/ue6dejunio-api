package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.CreateEventCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.UpdateEventCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateEventRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.EventResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateEventRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/assessment-events")
@Tag(
        name = "AssessmentEvents",
        description = "Criterios de una actividad. Su promedio es la nota del criterio")
@SecurityRequirement(name = "bearerAuth")
public class AssessmentEventController {

    private final IAssessmentEventService eventService;

    public AssessmentEventController(IAssessmentEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @PreAuthorize("@authz.canWriteScoreCriterion(authentication, #r.criterionId())")
    @Operation(summary = "Agregar un criterio a una actividad ya existente")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest r) {
        AssessmentEvent e = eventService.create(new CreateEventCommand(r.criterionId(), r.title()));
        return ResponseEntity.ok(EventResponse.from(e));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canReadScoreEvent(authentication, #id)")
    @Operation(summary = "Obtener criterio de actividad por id")
    public ResponseEntity<EventResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(EventResponse.from(eventService.getById(id)));
    }

    @GetMapping
    @PreAuthorize("@authz.canReadScoreCriterion(authentication, #criterionId)")
    @Operation(summary = "Listar los criterios de la actividad de un criterio")
    public ResponseEntity<List<EventResponse>> list(
            @RequestParam("id_criterion") UUID criterionId) {
        return ResponseEntity.ok(
                eventService.listByCriterion(criterionId).stream()
                        .map(EventResponse::from)
                        .toList());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canWriteScoreEvent(authentication, #id)")
    @Operation(summary = "Renombrar un criterio de actividad")
    public ResponseEntity<EventResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateEventRequest r) {
        AssessmentEvent e = eventService.update(id, new UpdateEventCommand(r.title()));
        return ResponseEntity.ok(EventResponse.from(e));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canWriteScoreEvent(authentication, #id)")
    @Operation(summary = "Eliminar un criterio de actividad (solo si no tiene notas)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
