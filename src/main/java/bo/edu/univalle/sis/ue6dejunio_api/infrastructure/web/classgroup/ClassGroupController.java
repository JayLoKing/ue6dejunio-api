package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ClassGroupResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateClassGroupRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/class-groups")
@Tag(name = "ClassGroups", description = "Materias dictadas a un curso (Director)")
@SecurityRequirement(name = "bearerAuth")
public class ClassGroupController {

    private final IClassGroupService classGroupService;

    public ClassGroupController(IClassGroupService classGroupService) {
        this.classGroupService = classGroupService;
    }

    @PostMapping
    @Operation(summary = "Asignar materias+docentes a un curso (N filas). UNIQUE(curso, materia)")
    public ResponseEntity<List<ClassGroupResponse>> create(@Valid @RequestBody CreateClassGroupRequest request) {
        List<CreateClassGroupCommand.Assignment> assignments = request.assignments().stream()
            .map(a -> new CreateClassGroupCommand.Assignment(a.subjectId(), a.teacherId()))
            .toList();
        List<ClassGroup> created = classGroupService.createForCourse(
            new CreateClassGroupCommand(request.courseId(), assignments));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(created.stream().map(ClassGroupResponse::from).toList());
    }

    @GetMapping
    @Operation(summary = "Listar materias de un curso")
    public ResponseEntity<List<ClassGroupResponse>> byCourse(@RequestParam("id_course") UUID courseId) {
        return ResponseEntity.ok(classGroupService.byCourse(courseId).stream()
            .map(ClassGroupResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener class_group por id")
    public ResponseEntity<ClassGroupResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ClassGroupResponse.from(classGroupService.getById(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Dar de baja materia del curso (is_active=false)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        classGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
