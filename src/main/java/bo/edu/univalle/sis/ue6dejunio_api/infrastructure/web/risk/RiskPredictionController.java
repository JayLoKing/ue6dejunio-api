package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.risk;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.StudentRisk;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskPredictionService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.InstitutionRiskEntryResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.RiskPredictionResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.RiskRunResponse;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.StudentRiskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The risk panel's endpoints.
 *
 * <p>Every one of them resolves ownership rather than trusting the role. A prediction names a
 * student, their category and their probability of failing, so an endpoint that answers on role
 * alone hands the risk roster of the whole school to any teacher account willing to try ids.
 */
@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "RiskPrediction", description = "Predicciones de riesgo académico")
@SecurityRequirement(name = "bearerAuth")
public class RiskPredictionController {

    /** The gestión the school keeps records for. Outside it a sweep is a silent no-op. */
    private static final int FIRST_YEAR_ON_RECORD = 2000;
    private static final int LAST_YEAR_ON_RECORD = 2100;

    private final IRiskPredictionService predictionService;

    public RiskPredictionController(IRiskPredictionService predictionService) {
        this.predictionService = predictionService;
    }

    /** The sweep, by hand. The Director's, because it runs the model over the whole school. */
    @PostMapping("/risk/predict-year")
    @PreAuthorize("hasRole('Director')")
    @Operation(summary = "Ejecuta el modelo predictivo para toda la gestión")
    public ResponseEntity<RiskRunResponse> predictYear(
        @RequestParam @Min(FIRST_YEAR_ON_RECORD) @Max(LAST_YEAR_ON_RECORD) int academicYear,
        @RequestParam @Min(1) @Max(3) int trimester
    ) {
        return ResponseEntity.ok(
            RiskRunResponse.from(predictionService.predictYear(academicYear, trimester)));
    }

    @PostMapping("/class-groups/{classGroupId}/risk/predict")
    @PreAuthorize("@authz.canWriteClassGroup(authentication, #classGroupId)")
    @Operation(summary = "Ejecuta el modelo predictivo para una materia")
    public ResponseEntity<RiskRunResponse> predictClassGroup(
        @PathVariable UUID classGroupId,
        @RequestParam @Min(1) @Max(3) int trimester
    ) {
        return ResponseEntity.ok(
            RiskRunResponse.from(predictionService.predictClassGroup(classGroupId, trimester)));
    }

    @GetMapping("/class-groups/{classGroupId}/risk")
    @PreAuthorize("@authz.canReadClassGroup(authentication, #classGroupId)")
    @Operation(summary = "Predicciones vigentes de una materia, las de peor riesgo primero")
    public ResponseEntity<List<StudentRiskResponse>> byClassGroup(
        @PathVariable UUID classGroupId,
        @RequestParam @Min(1) @Max(3) int trimester
    ) {
        return ResponseEntity.ok(toResponses(predictionService.byClassGroup(classGroupId, trimester)));
    }

    /**
     * {@code canReadCourseRoster} and not {@code canReadCourse}: the latter is homeroom-only, and a
     * technical teacher has no homeroom. This panel spans every subject of the course, so the
     * teacher who runs one of them belongs in it — gated on homeroom they would be refused a list
     * about the students they teach.
     */
    @GetMapping("/courses/{courseId}/risk")
    @PreAuthorize("@authz.canReadCourseRoster(authentication, #courseId)")
    @Operation(summary = "Predicciones vigentes de todas las materias de un curso")
    public ResponseEntity<List<StudentRiskResponse>> byCourse(
        @PathVariable UUID courseId,
        @RequestParam @Min(1) @Max(3) int trimester
    ) {
        return ResponseEntity.ok(toResponses(predictionService.byCourse(courseId, trimester)));
    }

    /**
     * The Director's list: the students of the whole school closest to failing, worst first.
     *
     * <p>The one endpoint here gated on role alone, and it is the right gate for this one: the
     * answer spans every course of the gestión, so there is no single course to resolve ownership
     * against. Nobody below the Director has a scope that covers it.
     */
    @GetMapping("/risk/institution")
    @PreAuthorize("hasRole('Director')")
    @Operation(summary = "Estudiantes en riesgo de toda la unidad educativa en una gestion: un "
        + "estudiante por fila con su peor materia, el de peor riesgo primero")
    public ResponseEntity<List<InstitutionRiskEntryResponse>> institutionRisk(
        // Required, unlike the listings above: a list spanning gestiones would rank a student of one
        // year against a student of another, and the paged course read is only sound inside one.
        @RequestParam("id_academic_year") Integer academicYearId,
        @RequestParam @Min(1) @Max(3) int trimester,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) int places
    ) {
        return ResponseEntity.ok(
            predictionService.institutionRisk(academicYearId, trimester, places).stream()
                .map(InstitutionRiskEntryResponse::from).toList());
    }

    /** One student across every subject they sit — the view a tutor opens before talking to them. */
    @GetMapping("/students/{studentId}/risk")
    @PreAuthorize("@authz.canReadStudent(authentication, #studentId)")
    @Operation(summary = "Predicciones vigentes de un estudiante en todas sus materias")
    public ResponseEntity<List<StudentRiskResponse>> byStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok(toResponses(predictionService.byStudent(studentId)));
    }

    /**
     * Records that somebody acted on a prediction.
     *
     * <p>Guarded through the prediction's own subject. The only writable field is a flag, but the
     * answer carries the student and their risk — so role alone would let any teacher read a
     * prediction from a course they do not teach by guessing at ids.
     */
    @PutMapping("/risk-predictions/{id}/attend")
    @PreAuthorize("@authz.canWriteRiskPrediction(authentication, #id)")
    @Operation(summary = "Marca una predicción como atendida")
    public ResponseEntity<RiskPredictionResponse> markAttended(
        @PathVariable UUID id,
        @RequestParam boolean attended
    ) {
        return ResponseEntity.ok(
            RiskPredictionResponse.from(predictionService.markAttended(id, attended)));
    }

    private static List<StudentRiskResponse> toResponses(List<StudentRisk> risks) {
        return risks.stream().map(StudentRiskResponse::from).toList();
    }
}
