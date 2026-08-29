package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcEntry;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcSubject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One month's plan as the client reads it. The subject blocks travel with it because the form and
 * its preview render the whole document at once; a listing row carries an empty list instead.
 */
public record PdcResponse(
    UUID id,
    UUID courseId,
    String courseName,
    String gradeName,
    String parallelName,
    String levelName,
    UUID homeroomTeacherId,
    String homeroomTeacherName,
    List<String> teacherNames,
    Integer planNumber,
    Integer trimester,
    LocalDate periodStart,
    LocalDate periodEnd,
    String status,
    String reviewObservations,
    String holisticObjective,
    String finalProduct,
    String bibliography,
    UUID sourcePlanId,
    List<Subject> subjects,
    UUID createdById,
    UUID updatedById,
    String updatedByName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {

    public record Subject(
        UUID id,
        UUID classGroupId,
        String subjectName,
        String knowledgeArea,
        UUID teacherId,
        String teacherName,
        String learningObjective,
        String generalAdaptations,
        Integer displayOrder,
        List<Entry> entries
    ) {
        static Subject from(PdcSubject s) {
            return new Subject(
                s.id(), s.classGroupId(), s.subjectName(), s.knowledgeArea(),
                s.teacherId(), s.teacherName(), s.learningObjective(),
                s.generalAdaptations(), s.displayOrder(),
                s.entries().stream().map(Entry::from).toList());
        }
    }

    public record Entry(
        UUID id,
        String weekLabel,
        String contents,
        String practice,
        String theory,
        String valuation,
        String production,
        String resources,
        Integer periods,
        String criteriaBeing,
        String criteriaKnowing,
        String criteriaDoing,
        Integer displayOrder
    ) {
        static Entry from(PdcEntry e) {
            return new Entry(
                e.id(), e.weekLabel(), e.contents(), e.practice(), e.theory(),
                e.valuation(), e.production(), e.resources(), e.periods(),
                e.criteriaBeing(), e.criteriaKnowing(), e.criteriaDoing(),
                e.displayOrder());
        }
    }

    public static PdcResponse from(Pdc p) {
        return new PdcResponse(
            p.getId(), p.getCourseId(), p.getCourseName(), p.getGradeName(), p.getParallelName(),
            p.getLevelName(), p.getHomeroomTeacherId(), p.getHomeroomTeacherName(),
            p.getTeacherNames(),
            p.getPlanNumber(), p.getTrimester(), p.getPeriodStart(), p.getPeriodEnd(),
            p.getStatus(), p.getReviewObservations(), p.getHolisticObjective(),
            p.getFinalProduct(), p.getBibliography(), p.getSourcePlanId(),
            p.getSubjects().stream().map(Subject::from).toList(),
            p.getCreatedById(), p.getUpdatedById(), p.getUpdatedByName(),
            p.getCreatedAt(), p.getUpdatedAt());
    }
}
