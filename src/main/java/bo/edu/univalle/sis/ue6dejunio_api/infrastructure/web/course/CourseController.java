package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.course;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CourseWithSubjects;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.CreateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.UpdateCourseCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.attendance.IAttendanceService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.gradebook.IGradebookService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ClassGroupResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CourseAttendanceStatsResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CourseOverviewResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CourseResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CourseWithSubjectsResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateCourseRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.HomeroomTeacherRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.PagedResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.ReassignTeacherRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateCourseRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/courses")
@Tag(name = "Courses", description = "Cursos (grado+paralelo+anio) con docente de aula (Director)")
@SecurityRequirement(name = "bearerAuth")
public class CourseController {

    private final ICourseService courseService;
    private final IClassGroupService classGroupService;
    private final IGradebookService gradebookService;
    private final IAttendanceService attendanceService;

    public CourseController(ICourseService courseService, IClassGroupService classGroupService,
                            IGradebookService gradebookService, IAttendanceService attendanceService) {
        this.courseService = courseService;
        this.classGroupService = classGroupService;
        this.gradebookService = gradebookService;
        this.attendanceService = attendanceService;
    }

    @PostMapping
    @Operation(summary = "Crear curso + materias (1 transaccion: courses + class_groups). Anio auto")
    public ResponseEntity<CourseWithSubjectsResponse> create(@Valid @RequestBody CreateCourseRequest r) {
        List<CreateCourseCommand.Assignment> assignments = r.assignments().stream()
            .map(a -> new CreateCourseCommand.Assignment(a.subjectId(), a.teacherId()))
            .toList();
        CourseWithSubjects created = courseService.create(new CreateCourseCommand(
            r.gradeId(), r.parallelId(), r.homeroomTeacherId(), assignments));
        return ResponseEntity.ok(CourseWithSubjectsResponse.from(created));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener curso por id")
    public ResponseEntity<CourseResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(CourseResponse.from(courseService.getById(id)));
    }

    @GetMapping
    @Operation(summary = "Listar cursos. id_academic_year opcional filtra")
    public ResponseEntity<PagedResponse<CourseResponse>> list(
        @RequestParam(value = "id_academic_year", required = false) Integer academicYearId,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit
    ) {
        PageQuery p = PageQuery.of(offset - 1, limit);
        return ResponseEntity.ok(PagedResponse.of(
            courseService.list(academicYearId, p).map(CourseResponse::from)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar curso (docente de aula, activo)")
    public ResponseEntity<CourseResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateCourseRequest r) {
        Course c = courseService.update(id, new UpdateCourseCommand(r.homeroomTeacherId(), r.active()));
        return ResponseEntity.ok(CourseResponse.from(c));
    }

    @PutMapping("/{id}/homeroom-teacher")
    @Operation(summary = "Asignar docente de aula al curso")
    public ResponseEntity<CourseResponse> setHomeroom(@PathVariable UUID id,
                                                     @Valid @RequestBody HomeroomTeacherRequest r) {
        return ResponseEntity.ok(CourseResponse.from(
            courseService.setHomeroomTeacher(id, r.homeroomTeacherId())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Dar de baja curso (is_active=false)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        courseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/overview")
    @PreAuthorize("@authz.canReadCourse(authentication, #id)")
    @Operation(summary = "Vista consolidada del curso: header, docente de aula, class_groups y "
        + "estudiantes con notas del trimestre (paginado)")
    public ResponseEntity<CourseOverviewResponse> overview(
        @PathVariable UUID id,
        @RequestParam @Min(1) @Max(3) Integer trimester,
        @RequestParam(defaultValue = "1") @Min(1) int offset,
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) int limit
    ) {
        PageQuery p = PageQuery.of(offset - 1, limit, SortField.asc("student.lastNames"), SortField.asc("student.names"));
        return ResponseEntity.ok(CourseOverviewResponse.from(
            gradebookService.courseOverview(id, trimester, p)));
    }

    @GetMapping("/{id}/attendance-stats")
    @PreAuthorize("@authz.canReadCourse(authentication, #id)")
    @Operation(summary = "Estadisticas de asistencia diaria del curso (id_class_group NULL). "
        + "trimester opcional: presente -> alcance trimestral, ausente -> alcance anual")
    public ResponseEntity<CourseAttendanceStatsResponse> attendanceStats(
        @PathVariable UUID id,
        @RequestParam(required = false) @Min(1) @Max(3) Integer trimester
    ) {
        return ResponseEntity.ok(CourseAttendanceStatsResponse.from(
            attendanceService.attendanceStats(id, trimester)));
    }

    @PutMapping("/{courseId}/class-groups/{classGroupId}/teacher")
    @Operation(summary = "Reasignar docente de un class group del curso")
    public ResponseEntity<ClassGroupResponse> reassignTeacher(@PathVariable UUID courseId,
                                                              @PathVariable UUID classGroupId,
                                                              @Valid @RequestBody ReassignTeacherRequest r) {
        ClassGroup updated = classGroupService.reassignTeacher(courseId, classGroupId, r.teacherId());
        return ResponseEntity.ok(ClassGroupResponse.from(updated));
    }
}
