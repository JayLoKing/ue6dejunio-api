package bo.edu.univalle.sis.ue6dejunio_api.application.services.assessment;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentDimension;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentEvent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.AssessmentScore;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment.CriterionAvg;
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

    private static final BigDecimal HUNDRED = new BigDecimal("100");

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
        if (c.score().compareTo(BigDecimal.ZERO) < 0 || c.score().compareTo(event.maxScore()) > 0) {
            throw new IllegalArgumentException("Nota fuera de rango (0-" + event.maxScore() + ")");
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

    private void consolidate(UUID courseEnrollmentId, UUID classGroupId, Integer trimester, UUID createdBy) {
        List<CriterionAvg> aggs = scoreDomain.criterionAveragesForConsolidation(
            courseEnrollmentId, classGroupId, trimester);
        BigDecimal being = BigDecimal.ZERO;
        BigDecimal knowing = BigDecimal.ZERO;
        BigDecimal doing = BigDecimal.ZERO;
        BigDecimal deciding = BigDecimal.ZERO;

        for (CriterionAvg a : aggs) {
            BigDecimal avg = a.avgScore() != null ? a.avgScore() : BigDecimal.ZERO;
            BigDecimal weighted = avg.divide(HUNDRED, 6, RoundingMode.HALF_UP).multiply(a.maxWeight());
            switch (a.dimension()) {
                case AssessmentDimension.BEING -> being = being.add(weighted);
                case AssessmentDimension.KNOWING -> knowing = knowing.add(weighted);
                case AssessmentDimension.DOING -> doing = doing.add(weighted);
                case AssessmentDimension.DECIDING -> deciding = deciding.add(weighted);
                default -> { }
            }
        }
        UUID academicScoreId = academicScoreDomain.ensureAcademicScore(
            courseEnrollmentId, classGroupId, trimester, createdBy);
        academicScoreDomain.setDimensions(academicScoreId,
            scale(being), scale(knowing), scale(doing), scale(deciding));
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
