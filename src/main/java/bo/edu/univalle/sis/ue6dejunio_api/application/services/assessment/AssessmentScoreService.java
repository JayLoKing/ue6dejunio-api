package bo.edu.univalle.sis.ue6dejunio_api.application.services.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentDimension;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.DimensionAvg;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.SetScoreCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.RiskInputsChanged;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.criterion.ICriterionDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class AssessmentScoreService implements IAssessmentScoreService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentScoreService.class);

    private final IAssessmentScoreDomain scoreDomain;
    private final IAssessmentEventDomain eventDomain;
    private final ICriterionDomain criterionDomain;
    private final IScoreDomain academicScoreDomain;
    private final IClassGroupDomain classGroupDomain;
    private final IDomainEventPublisher events;

    public AssessmentScoreService(IAssessmentScoreDomain scoreDomain,
                                  IAssessmentEventDomain eventDomain,
                                  ICriterionDomain criterionDomain,
                                  IScoreDomain academicScoreDomain,
                                  IClassGroupDomain classGroupDomain,
                                  IDomainEventPublisher events) {
        this.scoreDomain = scoreDomain;
        this.eventDomain = eventDomain;
        this.criterionDomain = criterionDomain;
        this.academicScoreDomain = academicScoreDomain;
        this.classGroupDomain = classGroupDomain;
        this.events = events;
    }

    /**
     * A score targets an activity item or a criterion, never both. Whichever it is, the write ends
     * in the same consolidation, so the two paths differ only in how the target is resolved.
     */
    @Override
    @Transactional
    public AssessmentScore setScore(SetScoreCommand c) {
        if ((c.eventId() == null) == (c.criterionId() == null)) {
            throw new ValidationException(
                "Indique exactamente un destino para la nota: actividad o criterio");
        }

        ScoreTarget target = c.targetsEvent() ? resolveEvent(c.eventId()) : resolveCriterion(c.criterionId());

        UUID enrollmentCourse = scoreDomain.courseOfCourseEnrollment(c.courseEnrollmentId());
        UUID classGroupCourse = classGroupDomain.courseIdOfClassGroup(target.classGroupId());
        if (!enrollmentCourse.equals(classGroupCourse)) {
            throw new ValidationException(
                "El estudiante no pertenece al curso de la materia evaluada");
        }

        // A score may not exceed the cap of its criterion's dimension. The dimension is guarded by
        // a CHECK constraint, so an unknown one means the row drifted from the schema -- a
        // conflict to report, not the raw IllegalArgumentException the lookup would raise.
        if (!AssessmentDimension.isValid(target.dimension())) {
            throw new ConflictException(
                "El criterio tiene una dimension desconocida: " + target.dimension());
        }
        BigDecimal dimensionMax = AssessmentDimension.max(target.dimension());
        if (c.score().compareTo(BigDecimal.ZERO) < 0 || c.score().compareTo(dimensionMax) > 0) {
            throw new ValidationException(
                "Nota fuera de rango para " + target.dimension() + " (0-" + dimensionMax + ")");
        }

        AssessmentScore saved = c.targetsEvent()
            ? scoreDomain.upsertForEvent(c.courseEnrollmentId(), c.eventId(), c.score())
            : scoreDomain.upsertForCriterion(c.courseEnrollmentId(), c.criterionId(), c.score());

        consolidate(c.courseEnrollmentId(), target.classGroupId(), target.trimester(), c.createdBy());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentScore> listByEvent(UUID eventId) {
        return scoreDomain.listByEvent(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentScore> listByCriterion(UUID criterionId) {
        return scoreDomain.listByCriterion(criterionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentScore> listByCourseEnrollment(UUID courseEnrollmentId) {
        return scoreDomain.listByCourseEnrollment(courseEnrollmentId);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        AssessmentScore existing = scoreDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("AssessmentScore", id));
        // Locate, do not re-validate: the write guards would refuse a direct score sitting on a
        // criterion that also holds items, leaving that row impossible to delete. Removing it is
        // precisely how such a row gets cleaned up.
        ScoreTarget target = existing.eventId() != null
            ? resolveEvent(existing.eventId())
            : locateCriterion(existing.criterionId());
        scoreDomain.deleteById(id);
        consolidate(existing.courseEnrollmentId(), target.classGroupId(), target.trimester(), null);
    }

    private ScoreTarget resolveEvent(UUID eventId) {
        AssessmentEvent event = eventDomain.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("AssessmentEvent", eventId));
        return new ScoreTarget(event.classGroupId(), event.trimester(), event.dimension());
    }

    /** Where the criterion consolidates, with no opinion on whether it may be scored. */
    private ScoreTarget locateCriterion(UUID criterionId) {
        EvaluationCriterion criterion = criterionDomain.findById(criterionId)
            .orElseThrow(() -> new ResourceNotFoundException("Criterion", criterionId));
        return new ScoreTarget(criterion.classGroupId(), criterion.trimester(), criterion.dimension());
    }

    /**
     * A criterion fed by an activity is scored through its items; letting it also take a direct
     * score would give the same criterion two answers for one student.
     *
     * <p>Holding items is what disqualifies it, not the activity name. Criteria created before
     * {@code activity_name} existed carry items with a null name, and checking the name alone
     * would wave those straight through.
     */
    private ScoreTarget resolveCriterion(UUID criterionId) {
        EvaluationCriterion criterion = criterionDomain.findById(criterionId)
            .orElseThrow(() -> new ResourceNotFoundException("Criterion", criterionId));
        if (criterion.isActivityBased()) {
            throw new ConflictException(
                "El criterio pertenece a la actividad \"" + criterion.activityName()
                + "\": califique sus criterios, no el criterio agrupador");
        }
        if (eventDomain.hasItems(criterionId)) {
            throw new ConflictException(
                "El criterio \"" + criterion.name() + "\" ya tiene criterios de actividad"
                + " registrados: califique esos, no el criterio agrupador");
        }
        return new ScoreTarget(criterion.classGroupId(), criterion.trimester(), criterion.dimension());
    }

    // Consolidation: average of each dimension's criteria. An activity-based criterion arrives
    // already averaged from the query, so it weighs the same as one scored directly.
    // total_score = sum of the four (GENERATED column in the database).
    private void consolidate(UUID courseEnrollmentId, UUID classGroupId, Integer trimester, UUID createdBy) {
        List<DimensionAvg> avgs = scoreDomain.dimensionAverages(courseEnrollmentId, classGroupId, trimester);
        BigDecimal being = BigDecimal.ZERO;
        BigDecimal knowing = BigDecimal.ZERO;
        BigDecimal doing = BigDecimal.ZERO;
        BigDecimal deciding = BigDecimal.ZERO;

        for (DimensionAvg a : avgs) {
            BigDecimal avg = a.avgScore() != null ? scale(a.avgScore()) : BigDecimal.ZERO;
            switch (a.dimension()) {
                case AssessmentDimension.BEING -> being = avg;
                case AssessmentDimension.KNOWING -> knowing = avg;
                case AssessmentDimension.DOING -> doing = avg;
                case AssessmentDimension.DECIDING -> deciding = avg;
                // Dropping it would lower total_score with nothing to show for it. The row is
                // still skipped -- there is no column to put it in -- but never in silence.
                default -> log.warn(
                    "Unknown dimension '{}' while consolidating enrollment {}: row skipped",
                    a.dimension(), courseEnrollmentId);
            }
        }
        checkCap(being, AssessmentDimension.BEING);
        checkCap(knowing, AssessmentDimension.KNOWING);
        checkCap(doing, AssessmentDimension.DOING);
        checkCap(deciding, AssessmentDimension.DECIDING);

        UUID academicScoreId = academicScoreDomain.ensureAcademicScore(
            courseEnrollmentId, classGroupId, trimester, createdBy);
        academicScoreDomain.setDimensions(academicScoreId, being, knowing, doing, deciding);

        // Every path that writes, corrects or removes a mark ends here, which is why the risk model
        // is told here and not at each of them. It is stated, not acted on: what a changed mark
        // means for a prediction is the model's business, and this service does not know one exists.
        if (trimester != null) {
            events.publish(new RiskInputsChanged(classGroupId, trimester));
        }
    }

    private void checkCap(BigDecimal value, String dimension) {
        BigDecimal max = AssessmentDimension.max(dimension);
        if (value.compareTo(max) > 0) {
            throw new ConflictException(
                "El promedio de " + dimension + " (" + value + ") excede el tope " + max
                + ". Revise que las casillas esten en escala 0-" + max
                + " (posibles notas antiguas en escala 0-100).");
        }
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Where a score lands: the class group, trimester and dimension it consolidates into. */
    private record ScoreTarget(UUID classGroupId, Integer trimester, String dimension) {}
}
