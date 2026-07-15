package bo.edu.univalle.sis.ue6dejunio_api.application.services.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentDimension;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.DimensionAvg;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.SetScoreCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentEventDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.assessment.IAssessmentScoreService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.score.IScoreDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class AssessmentScoreService implements IAssessmentScoreService {

    private final IAssessmentScoreDomain scoreDomain;
    private final IAssessmentEventDomain eventDomain;
    private final IScoreDomain academicScoreDomain;
    private final IClassGroupDomain classGroupDomain;

    public AssessmentScoreService(IAssessmentScoreDomain scoreDomain,
                                  IAssessmentEventDomain eventDomain,
                                  IScoreDomain academicScoreDomain,
                                  IClassGroupDomain classGroupDomain) {
        this.scoreDomain = scoreDomain;
        this.eventDomain = eventDomain;
        this.academicScoreDomain = academicScoreDomain;
        this.classGroupDomain = classGroupDomain;
    }

    @Override
    @Transactional
    public AssessmentScore setScore(SetScoreCommand c) {
        AssessmentEvent event = eventDomain.findById(c.eventId())
            .orElseThrow(() -> new ResourceNotFoundException("AssessmentEvent", c.eventId()));

        UUID enrollmentCourse = scoreDomain.courseOfCourseEnrollment(c.courseEnrollmentId());
        UUID classGroupCourse = classGroupDomain.courseIdOfClassGroup(event.classGroupId());
        if (!enrollmentCourse.equals(classGroupCourse)) {
            throw new IllegalArgumentException(
                "El estudiante no pertenece al curso de la materia evaluada");
        }

        // La nota no puede exceder el tope de la dimension del criterio
        BigDecimal dimensionMax = AssessmentDimension.max(event.dimension());
        if (c.score().compareTo(BigDecimal.ZERO) < 0 || c.score().compareTo(dimensionMax) > 0) {
            throw new IllegalArgumentException(
                "Nota fuera de rango para " + event.dimension() + " (0-" + dimensionMax + ")");
        }

        AssessmentScore saved = scoreDomain.upsert(c.courseEnrollmentId(), c.eventId(), c.score());
        consolidate(c.courseEnrollmentId(), event.classGroupId(), event.trimester(), c.createdBy());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssessmentScore> listByEvent(UUID eventId) {
        return scoreDomain.listByEvent(eventId);
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
        AssessmentEvent event = eventDomain.findById(existing.eventId())
            .orElseThrow(() -> new ResourceNotFoundException("AssessmentEvent", existing.eventId()));
        scoreDomain.deleteById(id);
        consolidate(existing.courseEnrollmentId(), event.classGroupId(), event.trimester(), null);
    }

    // Consolidado: promedio simple de las notas registradas de cada dimension.
    // total_score = suma de las 4 (columna GENERATED en BD).
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
                default -> { }
            }
        }
        checkCap(being, AssessmentDimension.BEING);
        checkCap(knowing, AssessmentDimension.KNOWING);
        checkCap(doing, AssessmentDimension.DOING);
        checkCap(deciding, AssessmentDimension.DECIDING);

        UUID academicScoreId = academicScoreDomain.ensureAcademicScore(
            courseEnrollmentId, classGroupId, trimester, createdBy);
        academicScoreDomain.setDimensions(academicScoreId, being, knowing, doing, deciding);
    }

    private void checkCap(BigDecimal value, String dimension) {
        BigDecimal max = AssessmentDimension.max(dimension);
        if (value.compareTo(max) > 0) {
            throw new IllegalArgumentException(
                "El promedio de " + dimension + " (" + value + ") excede el tope " + max
                + ". Revise que las casillas esten en escala 0-" + max
                + " (posibles notas antiguas en escala 0-100).");
        }
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
