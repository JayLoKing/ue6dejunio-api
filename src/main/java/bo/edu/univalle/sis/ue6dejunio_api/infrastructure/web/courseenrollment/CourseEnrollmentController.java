package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.courseenrollment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment.EnrollToCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CourseStudentResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateStudentRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.EnrollCourseRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.EnrollResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.EnrollStudentRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/course-enrollments")
@Tag(
        name = "CourseEnrollments",
        description = "Inscripcion de estudiantes a un curso (una vez por gestion)")
@SecurityRequirement(name = "bearerAuth")
public class CourseEnrollmentController {

    private final ICourseEnrollmentService enrollmentService;

    public CourseEnrollmentController(ICourseEnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    private static CreateStudentCommand toCommand(CreateStudentRequest s) {
        return new CreateStudentCommand(
                s.rudeCode(),
                s.identityCard(),
                s.names(),
                s.lastNames(),
                s.birthDate(),
                s.gender());
    }

    @PostMapping
    @PreAuthorize("@authz.canWriteCourseEnrollment(authentication, #request.courseId())")
    @Operation(summary = "Inscribir un estudiante al curso (registro manual)")
    public ResponseEntity<EnrollResponse> enrollSingle(
            @Valid @RequestBody EnrollStudentRequest request, JwtAuthenticationToken token) {
        EnrollResult result =
                enrollmentService.enroll(
                        new EnrollToCourseCommand(
                                request.courseId(),
                                List.of(toCommand(request.student())),
                                currentUser(token)));
        return ResponseEntity.ok(EnrollResponse.from(result));
    }

    @PostMapping("/sync")
    @PreAuthorize("@authz.canWriteCourseEnrollment(authentication, #request.courseId())")
    @Operation(
            summary =
                    "Sincronizar nomina (PDF): crea estudiantes e inscribe al curso. Transaccion ACID")
    public ResponseEntity<EnrollResponse> sync(
            @Valid @RequestBody EnrollCourseRequest request, JwtAuthenticationToken token) {
        List<CreateStudentCommand> students =
                request.students().stream().map(CourseEnrollmentController::toCommand).toList();
        EnrollResult result =
                enrollmentService.enroll(
                        new EnrollToCourseCommand(
                                request.courseId(), students, currentUser(token)));
        return ResponseEntity.ok(EnrollResponse.from(result));
    }

    /**
     * The caller's id, for the row that records who put a student back on the roll.
     *
     * <p>Refused rather than left null when the token carries no usable subject: an audit column
     * that silently says "nobody" reads as a decision the school made anonymously.
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

    @GetMapping
    @PreAuthorize("@authz.canReadCourseRoster(authentication, #courseId)")
    @Operation(summary = "Listar estudiantes inscritos a un curso")
    public ResponseEntity<PagedResponse<CourseStudentResponse>> students(
            @RequestParam("id_course") UUID courseId,
            @RequestParam(defaultValue = "1") @Min(1) int offset,
            @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit) {
        PageQuery p =
                PageQuery.of(
                        offset - 1,
                        limit,
                        SortField.asc("student.lastNames"),
                        SortField.asc("student.names"));
        return ResponseEntity.ok(
                PagedResponse.of(
                        enrollmentService
                                .studentsOfCourse(courseId, p)
                                .map(CourseStudentResponse::from)));
    }
}
