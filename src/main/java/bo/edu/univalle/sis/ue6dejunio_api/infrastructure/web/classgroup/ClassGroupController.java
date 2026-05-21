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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/class-groups")
@Tag(name = "ClassGroups", description = "Asignacion de materias a cursos (Director)")
@SecurityRequirement(name = "bearerAuth")
public class ClassGroupController {

    private final IClassGroupService classGroupService;

    public ClassGroupController(IClassGroupService classGroupService) {
        this.classGroupService = classGroupService;
    }

    @PostMapping
    @Operation(summary = "Crear curso: asigna materias+docentes a un grado/paralelo. Anio automatico")
    public ResponseEntity<List<ClassGroupResponse>> create(@Valid @RequestBody CreateClassGroupRequest request) {
        List<CreateClassGroupCommand.Assignment> assignments = request.assignments().stream()
            .map(a -> new CreateClassGroupCommand.Assignment(a.subjectId(), a.teacherId()))
            .toList();
        List<ClassGroup> created = classGroupService.createCourse(
            new CreateClassGroupCommand(request.gradeId(), request.parallelId(), assignments));
        List<ClassGroupResponse> body = created.stream().map(ClassGroupResponse::from).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
