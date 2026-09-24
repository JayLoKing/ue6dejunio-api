package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryScope;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawalReason;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.WithdrawStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security.AuthorizationComponent;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentDirectoryResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.WithdrawStudentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/students")
@Tag(name = "Students", description = "Consulta de estudiantes")
@SecurityRequirement(name = "bearerAuth")
public class StudentController {

    private final IStudentService studentService;
    private final AuthorizationComponent authz;

    public StudentController(IStudentService studentService, AuthorizationComponent authz) {
        this.studentService = studentService;
        this.authz = authz;
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authz.canReadStudent(authentication, #id)")
    @Operation(summary = "Obtener estudiante por id")
    public ResponseEntity<StudentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(StudentResponse.from(studentService.getById(id)));
    }

    /**
     * The institution's students, for whoever is entitled to see them.
     *
     * <p>One endpoint rather than two. A Director's listing and a teacher's picker ask the same
     * question with different filters, and the answer to "which students may this caller see" is
     * already {@code effectiveDirectoryCourseId}: the Director and the secretariat span the school,
     * a teacher is pinned to their own course whatever they send.
     *
     * @param scope ACTIVE, WITHDRAWN or ALL. Absent means ACTIVE — what every caller meant before
     *     this filter existed, so an old caller keeps getting exactly what it got
     */
    @GetMapping("/search")
    @Operation(
            summary =
                    "Buscar estudiantes por nombre, apellido, RUDE o carnet. "
                            + "Filtros: curso, grado, paralelo, gestion y estado. Sin gestion responde por la actual. "
                            + "Docente acotado a su curso de aula")
    public ResponseEntity<PagedResponse<StudentDirectoryResponse>> search(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) Integer gradeId,
            @RequestParam(required = false) Integer parallelId,
            @RequestParam(required = false) Integer academicYearId,
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "1") @Min(1) int offset,
            @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit,
            Authentication authentication) {
        // A scope outside the set is a caller mistake, not an empty page: read as "no filter" it
        // would quietly answer with the active students and look like the school lost the rest.
        StudentDirectoryScope effectiveScope =
                scope == null || scope.isBlank()
                        ? StudentDirectoryScope.ACTIVE
                        : StudentDirectoryScope.fromRequestValue(scope)
                                .orElseThrow(
                                        () ->
                                                new ValidationException(
                                                        "Alcance invalido: " + scope));
        UUID effectiveCourseId = authz.effectiveDirectoryCourseId(authentication, courseId);
        PageQuery pageQuery = PageQuery.of(offset - 1, limit, SortField.asc("id"));
        StudentDirectoryQuery query =
                new StudentDirectoryQuery(
                        q, effectiveCourseId, gradeId, parallelId, academicYearId, effectiveScope);
        return ResponseEntity.ok(
                PagedResponse.of(
                        studentService
                                .search(query, pageQuery)
                                .map(StudentDirectoryResponse::from)));
    }

    @PostMapping("/{id}/withdraw")
    // The Director's, and nobody else's. A teacher registers and corrects the students of their
    // own course; taking one off the roll ends their enrolments and drops them from every listing,
    // and that is a decision the school makes once, not one a course makes about its own roster.
    @PreAuthorize("hasRole('Director')")
    @Operation(summary = "Baja logica de estudiante, solo Director (retiro/transferencia/otro)")
    public ResponseEntity<Void> withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawStudentRequest request,
            JwtAuthenticationToken token) {
        StudentWithdrawalReason reason =
                StudentWithdrawalReason.fromRequestValue(request.reason())
                        .orElseThrow(
                                () ->
                                        new ValidationException(
                                                "Motivo de baja invalido: " + request.reason()));
        studentService.withdraw(
                new WithdrawStudentCommand(id, reason, request.note(), currentUser(token)));
        return ResponseEntity.noContent().build();
    }

    /**
     * The caller's id, for the row that records who decided.
     *
     * <p>Refused rather than left null when the token carries no usable subject: an audit column
     * that silently says "nobody" is worse than a denial, because it reads as a decision the school
     * made anonymously.
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
}
