package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.teacher;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.courseenrollment.ICourseEnrollmentService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ClassGroupResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CourseStudentResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/teachers")
@Tag(name = "Teachers", description = "Vistas del docente: sus materias y estudiantes de aula")
@SecurityRequirement(name = "bearerAuth")
public class TeacherController {

    private final IClassGroupService classGroupService;
    private final ICourseDomain courseDomain;
    private final ICourseEnrollmentService enrollmentService;

    public TeacherController(IClassGroupService classGroupService, ICourseDomain courseDomain,
                             ICourseEnrollmentService enrollmentService) {
        this.classGroupService = classGroupService;
        this.courseDomain = courseDomain;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/{userId}/class-groups")
    @PreAuthorize("@authz.canReadTeacherRoster(authentication, #userId)")
    @Operation(summary = "Materias (class_groups) que dicta el docente")
    public ResponseEntity<List<ClassGroupResponse>> classGroups(@PathVariable UUID userId) {
        return ResponseEntity.ok(classGroupService.byTeacher(userId).stream()
            .map(ClassGroupResponse::from).toList());
    }

    @GetMapping("/{userId}/students")
    @PreAuthorize("@authz.canReadTeacherRoster(authentication, #userId)")
    @Operation(summary = "Estudiantes del curso de aula del docente (homeroom). Paginado")
    public ResponseEntity<PagedResponse<CourseStudentResponse>> homeroomStudents(
        @PathVariable UUID userId,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit
    ) {
        Pageable p = PageRequest.of(offset - 1, limit, Sort.by("student.lastNames", "student.names"));
        Optional<Course> homeroom = courseDomain.homeroomCourseOf(userId);
        if (homeroom.isEmpty()) {
            Page<CourseStudentResponse> empty = new PageImpl<>(List.of(), p, 0);
            return ResponseEntity.ok(PagedResponse.of(empty));
        }
        return ResponseEntity.ok(PagedResponse.of(
            enrollmentService.studentsOfCourse(homeroom.get().id(), p).map(CourseStudentResponse::from)));
    }
}
